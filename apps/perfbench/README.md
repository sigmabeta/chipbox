# perfbench — host performance benchmarking CLI

The host-side home of the Chipbox performance harness: a JVM CLI that measures
**how fast the emulator cores render audio** (real-time factor, RTF) over a
corpus you supply. It is a **measurement, not a gate** — nothing in CI runs it.

Device axes (UI scroll jank, startup) live in the Android `:benchmark` module;
the result schema both homes share lives in [`:apps:perfbench-core`](../perfbench-core/README.md).

## Commands

### `audio --dir <corpus> [options]`

For each scenario (an emulator family), picks one representative track from the
corpus, warms up once, then times N renders through the **reused raw-emulator
core** and reports the median RTF. Writes `<out>/results.json`.

| option | meaning | default |
|---|---|---|
| `--dir <folder>` | corpus root to walk for audio files | **required** |
| `--only <a,b>` | only these scenario ids (`audio.usf`, `audio.vgm`, `audio.psf`) | all |
| `--repeat <n>` | timed repeats per scenario; the headline is the median | `3` |
| `--seconds <n>` | audio seconds rendered per repeat (the fixed sample wall) | `30` |
| `--out <dir>` | results root; `results.json` is written here | `build/perfbench` |

```sh
# every scenario, 30s per repeat, median of 3, into the default build/perfbench/
./gradlew :apps:perfbench:run --args="audio --dir /path/to/corpus"

# quick smoke over USF only
./gradlew :apps:perfbench:run --args="audio --dir /path/to/corpus --only audio.usf --seconds 5 --repeat 2"
```

The corpus is laid out as `.../<Game>/<track>.<ext>` — the same convention the
A/B harness uses. Chain/library files (`*.psflib`, `*.usflib`, …) must sit **next
to** the tracks that reference them, since the native loaders resolve them as
siblings. `build/perfbench/` is git-ignored; WAV scratch output lands in
`build/perfbench/wav/`.

## Reading the result

RTF = audio duration ÷ wall-clock render time, so **> 1 means faster than real
time**. The reported `rtf` uses the median wall time across the timed repeats,
so one noisy repeat can't dominate. Thread CPU seconds are recorded alongside as
a secondary metric (how much of the wall time was actual compute; omitted when
the JVM can't report it). Because the reused render path loads and tears down the
track on every call, each timed repeat covers load **and** render. Example:

```
audio.usf: <game> — <title>  RTF 23.83  (0.42s median for 10s audio)
audio.vgm: <game> — <title>  RTF 98.76  (0.10s median for 10s audio)
```

`results.json` carries the run config, the **host fingerprint** (CPU model and
cores, OS/kernel, JVM, and the Chipbox + sage commits), the corpus reference, and
an `audio` axis. Subsequent tickets populate the corpus manifest and add a
`diff` subcommand with comparability guards.

## Implementation note

No new render seam: the audio axis drives `EmulatorBank.render` from
[`:apps:abrender-core`](../abrender-core/README.md) unchanged. That path already
bypasses the base class's fade/length budget and renders a fixed wall of raw
samples, which is exactly what a repeatable benchmark wants. The native `.so`
files are reused from `:apps:jvm`'s host CMake build via the same
`hostNativeLibsElements` configuration `:apps:abrender` uses, so the CLI finds
them on `java.library.path` with no extra setup.
