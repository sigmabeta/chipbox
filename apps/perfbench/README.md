# perfbench — host performance benchmarking CLI

The host-side home of the Chipbox performance harness: a JVM CLI that measures
**how fast the emulator cores render audio** (real-time factor, RTF) over a
corpus you supply, generates a portable **library snapshot** from that corpus,
and diffs two runs against each other. It is a
**measurement, not a gate** — nothing in CI runs it.

Device axes (UI scroll jank, startup) live in the Android `:benchmark` module;
the result schema both homes share lives in [`:apps:perfbench-core`](../perfbench-core/README.md).

## Commands

### `audio --dir <corpus> [options]`

Renders **every supported file** in the corpus through the **reused raw-emulator
core**, one result per file keyed by its corpus-relative path. For each file it
warms up once, then times N renders and reports the median RTF. A file that fails
to render is recorded as an errored result (its error, no RTF) rather than
dropped, so a run never silently shrinks. Writes `<out>/results.json`.

| option | meaning | default |
|---|---|---|
| `--dir <folder>` | corpus root to walk for audio files | **required** |
| `--filter <glob>` | basename glob (`*.spc`) or corpus-relative path glob (`Chrono Trigger/**`) | whole corpus |
| `--repeat <n>` | timed repeats per file; the headline is the median | `3` |
| `--seconds <n>` | audio seconds rendered per repeat (the fixed sample wall) | `30` |
| `--out <dir>` | results root; `results.json` is written here | `build/perfbench` |

```sh
# whole corpus, 30s per repeat, median of 3, into the default build/perfbench/
./gradlew :apps:perfbench:run --args="audio --dir /path/to/corpus"

# quick smoke over one folder, 5s per repeat, median of 2
./gradlew :apps:perfbench:run --args="audio --dir /path/to/corpus --filter 'Chrono Trigger/**' --seconds 5 --repeat 2"
```

The corpus is laid out as `.../<Game>/<track>.<ext>` — the same convention the
A/B harness uses. Chain/library files (`*.psflib`, `*.usflib`, …) must sit **next
to** the tracks that reference them, since the native loaders resolve them as
siblings. `--filter` is a glob over each file's corpus-relative path: a pattern
with no `/` (`*.spc`) matches the **basename at any depth**, while one with `/`
(`Chrono Trigger/**`) matches the **whole relative path**, where `**` crosses
directories and `*` does not. `build/perfbench/` is git-ignored; WAV scratch
output lands in `build/perfbench/wav/`.

Output paths are relative to the module's working directory, so the default
`build/perfbench/` resolves to `apps/perfbench/build/perfbench/`, not the repo
root.

### `snapshot --dir <corpus> [--out <snapshot>]`

Runs the **real host scanner** once over the corpus and writes a portable
**library snapshot** — the scanner's raw game/track output (with chain files,
folder signatures, and scanner/reader versions) stored with paths **relative to
the corpus root**. The app ingests it (see the ingest capability) so a device
benchmark starts from a warm, deterministic library without paying for a scan on
every run.

| option | meaning | default |
|---|---|---|
| `--dir <folder>` | corpus root to scan | **required** |
| `--out <snapshot>` | snapshot file to write | `build/perfbench/library-snapshot.json` |

```sh
./gradlew :apps:perfbench:run --args="snapshot --dir /path/to/corpus --out build/perfbench/library-snapshot.json"
```

The scan uses the same readers, PSF chain resolution, and m3u overlays as the
app's library scan. Paths (track, folder, chain file, cover art) are stored
relative to `--dir` and the snapshot records a `root` reference, so the same
snapshot is valid on host and device: import rebases the relative paths onto the
device's corpus location.

### `diff --a <runA> --b <runB>`

Compares every axis of two runs — audio, UI, and startup — not just audio. Run
A is the **baseline**, run B the **current** run; each argument is a
`results.json` or the folder holding one. For every scenario measured by both
runs it prints the ratio — that axis's own primary cost metric (audio render
wall median, UI frame p50, startup time to first content p50) as baseline ÷
current, so **above 1 means B is better** — and the **geometric mean**, per axis
and overall.

```sh
./gradlew :apps:perfbench:run --args="diff --a build/perfbench-baseline --b build/perfbench"
```

The diff **refuses** rather than report a number when the two runs are not
comparable: a different **fingerprint class** (host vs device), a different
**scenario set**, or a different **corpus manifest**. The refusal names what
differed. The same machine class is required, not the same machine, so a
baseline captured on one host can be diffed against another host.

## Reading the result

RTF = audio duration ÷ wall-clock render time, so **> 1 means faster than real
time**. The reported `rtf` uses the median wall time across the timed repeats,
so one noisy repeat can't dominate. Thread CPU seconds are recorded alongside as
a secondary metric (how much of the wall time was actual compute; omitted when
the JVM can't report it). Because the reused render path loads and tears down the
track on every call, each timed repeat covers load **and** render. Each result is
keyed by its corpus-relative path; a file that would not render records its
`error` and a `null` RTF. Example:

```
Chrono Trigger/track.spc: <game> — <title>  RTF 23.83  (0.42s median for 10s audio)
```

`results.json` carries the run config, the **host fingerprint** (CPU model and
cores, OS/kernel, JVM, and the Chipbox + sage commits), the corpus reference
with its **manifest** — the `{path, sha256, bytes}` of every file the run read
(errored files included), ordered by path — and an `audio` axis. Compare two runs
with [`diff`](#diff---a-runa---b-runb).

## Implementation note

No new render seam: the audio axis drives `EmulatorBank.render` from
[`:apps:abrender-core`](../abrender-core/README.md) unchanged. That path already
bypasses the base class's fade/length budget and renders a fixed wall of raw
samples, which is exactly what a repeatable benchmark wants. The native `.so`
files are reused from `:apps:jvm`'s host CMake build via the same
`hostNativeLibsElements` configuration `:apps:abrender` uses, so the CLI finds
them on `java.library.path` with no extra setup.

## Tests

The `test` source set covers the CLI seams: the `audio`/`snapshot`/`diff` arg
parsers (`AudioArgsTest`, `SnapshotArgsTest`, `DiffArgsTest`), the corpus-manifest
construction and equality (`CorpusManifestTest`), and the scanner → snapshot →
import round trip (`SnapshotCommandTest`, `SnapshotRoundTripTest`). The shared
result schema, report math, and diff contract are tested in
[`:apps:perfbench-core`](../perfbench-core/README.md).
