# chipbox-abrender — emulator render test system

A headless test system for **verifying changes to any native emulator backend** (USF, PSF, SSF, GME,
VGM, 2SF, NCSF, GBA, vgmstream). It renders a directory of audio files to WAV + metrics, so you can
tell — empirically, across a whole library — whether a native-core change altered playback, fixed
something, or regressed it. It has two roles over the same engine:

- a **two-pass A/B tool** — render a baseline, change the native core, render a candidate, diff; and
- a **regression gate** — record a reference once, then fail unless a fresh render still matches it.

The engine lives in `:testing:abrender-core` and runs on **both the host JVM (x86_64) and a real
Android device (arm64)**. It walks a **directory of audio files** you point it at — no database,
scanner, or library to set up: drop a corpus folder on the machine, point it there, and go.

Everything is driven by **two scripts**, `record.sh` and `verify.sh`, each of which takes where to
render (`--where host|device`) and the corpus (`--corpus`):

```sh
# Golden gate: record once on the host, then verify the device matches.
scripts/abrender/record.sh --where host --corpus /path/to/corpus
scripts/abrender/verify.sh --where device --corpus /path/to/corpus

# Before/after A/B: record a baseline, change the native core, verify on the same machine.
scripts/abrender/record.sh --corpus /path/to/corpus --label baseline
#   … edit cbox/native/<emu>/, rebuild happens automatically on the next render …
scripts/abrender/verify.sh --corpus /path/to/corpus --against baseline
```

`record.sh --help` and `verify.sh --help` list every flag.

---

## 1. What you need

- This repo, buildable (`./gradlew` works).
- A JDK **with JNI headers** (`include/jni.h`) — needed to host-build the native libs. A plain JRE
  won't do. If the resolved JDK lacks headers, pass `-Pchipbox.jvm.nativeJdk=/path/to/jdk`.
- The native toolchain the libs need: `cmake` + a C/C++ compiler + `make` (Linux/macOS host build).
- A **corpus directory** of audio files for the format you're testing, e.g. a tree of `.usf`/
  `.miniusf` files. Chain/library files (`*.usflib`, `*.psflib`, `*_lib.*`) must sit **next to** the
  tracks that reference them — the native loaders resolve them as siblings.
- For `--where device`: a device on `adb` (or an emulator you started yourself).

The host path needs no device; rendering is offline and deterministic.

---

## 2. `record.sh` — render a corpus into a run

```
record.sh --where host|device --corpus DIR [options]
```

| option | meaning | default |
|---|---|---|
| `--where host\|device` | where to render (x86_64 JVM, or arm64 on a device) | `host` |
| `--corpus <dir>` | corpus root to walk for audio files | **required** |
| `--label <name>` | run name; output goes to `<out>/<name>/` | `reference` |
| `--out <dir>` | runs root | `<repo>/ab-runs` |
| `--seconds <n>` | seconds of audio rendered per track | `30` |
| `--ext <a,b>` | only these extensions, e.g. `usf,miniusf` | all supported |
| `--game <substr>` | only files whose **parent folder** name contains `<substr>` | — |
| `--every-song` | render every playable file (default: one per folder) | one per folder |
| `--subsong <n>` | subsong index for multi-song formats (NSF/GBS) | `0` |
| `--limit <n>` | cap the number of tracks (quick smoke run) | — |
| `--max-wall-seconds <n>` | per-track wall-clock cap | `120` |
| `--overwrite` | re-render tracks already present | resume/skip |
| `--parallel <n>` | host only: render with n JVMs over disjoint shards | `1` |
| `--serial <id>` | device only: target a specific `adb` device | the only one |
| `--skip-build` | device only: don't rebuild/reinstall the test APK | — |

**Selection.** Corpora are laid out as `.../<Game>/<track>.<ext>`, so the parent folder names the
game and the filename names the track. By default one representative track is rendered per folder
(the alphabetically-first playable file); `--every-song` renders all of them. A flat corpus (files
directly under `--corpus`) needs `--every-song`.

**Host vs device.** `--where host` runs the desktop CLI; `--parallel n` fans out n JVMs over disjoint
shards (one JVM can't render concurrently — singleton native state per backend) and merges them, which
is how a 400-game sweep finishes in minutes. `--where device` installs the self-contained device-test
APK, pushes the corpus, renders on arm64, and pulls the run back under `--out`.

---

## 3. `verify.sh` — render again and gate on the diff

```
verify.sh --where host|device --corpus DIR [--against NAME|PATH] [--tolerance auto|strict|cross-isa]
```

`verify.sh` renders the corpus **fresh** (on `--where`, via `record.sh`) and diffs it against a run
you recorded earlier. It exits non-zero if any track differs beyond the tolerance, so it drops into an
`if` or a CI step.

| option | meaning | default |
|---|---|---|
| `--where host\|device` | where to render this candidate | `host` |
| `--corpus <dir>` | corpus root to walk | **required** |
| `--against <name\|path>` | run to compare against: a label under `--out`, or a path | `reference` |
| `--out <dir>` | runs root | `<repo>/ab-runs` |
| `--tolerance <t>` | `auto` \| `strict` \| `cross-isa` (see §7) | `auto` |
| *(selection knobs)* | `--seconds/--ext/--game/--every-song/--subsong/--limit/--max-wall-seconds` | as recorded |
| `--parallel`, `--serial`, `--skip-build` | as in `record.sh` | |

Pass **the same selection knobs** you recorded the reference with, or the track sets differ and
`verify` reports extra/missing tracks.

### Reading the result

`verify` prints the per-verdict tally, likely regressions, and the most-changed tracks (with the path
of both WAVs) — then a final `CHECK: PASS` / `CHECK: FAIL` and a non-zero exit on failure. The candidate
run is left at `<out>/.verify/` so you can listen to any divergent track under its `wav/`.

**Verdicts**

| verdict | meaning |
|---|---|
| `IDENTICAL` | bit-for-bit equal PCM (hashes match) |
| `CHANGED` | both produced audio, but it differs |
| `REGRESSED_SILENT` | reference had audio, candidate is silent — **likely regression** |
| `NEW_ERROR` | reference rendered, candidate failed — **likely regression** |
| `FIXED_ERROR` | reference failed, candidate rendered — improvement |
| `STILL_ERROR` | both failed |
| `ONLY_IN_A` / `ONLY_IN_B` | track present in only one run (corpus/args mismatch) |

**Metrics**

- **`diffRMS`** (headline) — RMS of the sample-wise difference `A−B`, in dBFS. `-inf` = identical;
  the higher (closer to 0), the more the waveform changed.
- **`ΔRMS`** — change in the track's overall RMS level (candidate minus reference), in dB.
- **`maxΔ`** — largest single-sample difference (out of 32768).

**How to judge a result**

- **All `IDENTICAL`** → the change had **no effect on output**. Expected for a cosmetic / no-op /
  warning-only change.
- **`CHANGED` with very low `diffRMS`** (roughly `< -80 dBFS`, `maxΔ` a few LSB) → numerically
  negligible; almost certainly inaudible. Usually fine.
- **`CHANGED` with higher `diffRMS`** → audibly different. **Listen** to both WAVs (paths printed) and
  decide whether it's the intended improvement or a regression.
- **`REGRESSED_SILENT` / `NEW_ERROR`** → treat as a regression until proven otherwise.

A run where the *only* differences are intended (e.g. a known set of tracks a fix targets) and
everything else is `IDENTICAL` is the ideal "surgical change" signal.

---

## 4. Output layout

```
ab-runs/                                 # git-ignored
  reference/                             # a recorded run (record.sh --label reference)
    .provenance                          # where it was rendered (host/device) — used by --tolerance auto
    metrics.tsv                          # one row per track (see §5)
    wav/<game>__<title>__t<subsong>__<hash>.wav
  baseline/ …                            # other runs, by --label
  .verify/                               # the candidate verify.sh just rendered
    metrics.tsv
    wav/...
    diff-vs-<ref>.tsv                    # written by the diff, full per-track report
```

The WAVs are normal 16-bit stereo files — to compare a specific track by ear, open the same filename
under the reference's and the candidate's `wav/`.

---

## 5. `metrics.tsv` columns

Tab-separated, one row per track: `trackKey, game, title, ext, sampleRate, frames, rmsDbfs,
peakDbfs, lufs, pcmHash, error, wav`. `rmsDbfs`/`peakDbfs` are dBFS (`-inf` = silent), `lufs` is
BS.1770 integrated loudness (`NaN` if under ~400 ms), `pcmHash` is an FNV-1a hash of the rendered
PCM (the bit-identity check), `error` is empty on success.

---

## 6. Running unattended over a large corpus

- **Resumable.** Re-running the same `record.sh` skips tracks already present (matched by `metrics.tsv`
  + an existing WAV). A crashed or interrupted run continues where it left off; `--overwrite` forces a
  full re-render. (`verify.sh` always re-renders its candidate fresh.)
- **Isolated failures.** A bad/unloadable track records an `error` row and the run continues — one
  broken file never aborts a 400-game sweep.
- **Wall-clock cap.** `--max-wall-seconds` bounds a backend that produces audio forever-but-slowly. A
  single native call that wedges completely is not interruptible from the JVM (rare in practice); the
  renderer's watchdog records it as a failure so a resume skips past it.
- **Smoke first.** Add `--limit 5` to validate the corpus, extensions, and chain files before a full
  sweep.

---

## 7. Cross-ISA: why `--tolerance` exists

PCM should be the same on x86_64 and arm64, but a core that resamples through a `double` stage cannot
promise it: mGBA's GBA sinc resampler computes its kernel with `sin`/`cos`, and those are not
bit-identical between glibc (host) and Bionic (device), so a few samples land **one LSB** apart.

`verify.sh --tolerance auto` (the default) resolves this from each run's `.provenance` stamp: if the
reference and the fresh render came from different places it uses `cross-isa` (`maxΔ ≤ 1` **and**
diff-RMS ≤ −80 dBFS), otherwise `strict` (bit-identity). Override with `--tolerance strict` or
`--tolerance cross-isa`. A larger delta, a missing/extra track, or an error always fails. The
same-machine before/after A/B stays strict, so it still catches sub-LSB (rounding-only) changes.

---

## 8. Gotchas

- **Rebuild the native lib between passes.** For the host, `record.sh`/`verify.sh` depend on
  `:apps:jvm:chipboxHostNativeLibs` (input-tracked), so editing `cbox/native/<emu>/` rebuilds only the
  changed lib on the next render — no separate step. For `--where device`, `record.sh` reinstalls the
  test APK (unless `--skip-build`) so the arm64 cores are rebuilt too.
- **Chain/library files must be present as siblings.** `mini*` formats and PSF chains load a shared
  `*.usflib` / `*.psflib` / `*_lib.*` from the same directory as the track. If the corpus is missing
  them, those tracks fail with a load error.
- **Multi-subsong formats.** Directory mode renders one subsong per file (`--subsong`, default 0).
  Full per-subsong enumeration (NSF/GBS) isn't supported here. One-file-per-song formats (USF, the
  whole PSF family, SPC, VGM) are unaffected.
- **Determinism.** Rendering uses a fixed `--seconds` of raw samples with no fade and no
  declared-length budget, so output is reproducible and comparable across builds. Keep the selection
  knobs identical between the two sides.
- **Game grouping.** "One per game" means one file per leaf folder. If your corpus is flat, use
  `--every-song`.

---

## 9. On a device (`--where device`)

`record.sh --where device` builds + installs the self-contained device-test APK (all nine arm64 cores),
pushes the corpus to the test app's external files dir
(`/sdcard/Android/data/net.sigmabeta.chipbox.abrender.test/files/abrender`), renders on arm64, and
pulls the run into `--out`. `verify.sh --where device` does the same for its candidate and then diffs
it on the host against the reference — the comparison is architecture-independent code, so it never
needs to run on the device.

- `--serial <id>` pins a device when more than one is attached.
- `--skip-build` skips the APK rebuild/reinstall (corpus push + render only).
- A track that wedges in an uninterruptible native call halts the process (the engine's watchdog);
  re-run to resume past it — the on-device run dir keeps its progress just like the desktop one.

---

## 10. Under the hood

Both scripts are thin wrappers over the `abrender` CLI (`:testing:abrender`):

- `record.sh` → `abrender render` (host) or the `DeviceRenderTest` instrumentation (device).
- `verify.sh` → `record.sh` for the candidate, then `abrender diff --check --tolerance <t>`.

The engine that both use lives in `:testing:abrender-core`; see
[`testing/abrender-core/README.md`](../abrender-core/README.md) for the module boundary and the
tolerance semantics. Which `--ext` maps to which native lib:

| backend / `--ext` | lib |
|---|---|
| USF (`usf`, `miniusf`) | `libusf.so` |
| PSF family (`psf`, `minipsf`, `psf2`, …) | `libslopsf.so` |
| SSF/DSF (`ssf`, `dsf`) | `libssf.so` |
| GME (`spc`, `nsf`, `nsfe`, `gbs`) | `libgme.so` |
| VGM (`vgm`, `vgz`) | `libvgm.so` |
| 2SF (`twosf`, `mini2sf`) | `libtwosf.so` |
| NCSF (`ncsf`, `minincsf`) | `libncsf.so` |
| GBA (`gsf`, `minigsf`) | `libgba.so` |
| vgmstream (many) | `libvgmstream.so` |
