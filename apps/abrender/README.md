# chipbox-abrender — A/B render harness

A standalone headless JVM app for **A/B testing changes to any native emulator backend** (USF, PSF,
SSF, GME, VGM, 2SF, NCSF, GBA, vgmstream). It renders a fixed window of audio for each track in a
corpus to a WAV + a metrics row, and diffs two runs so you can tell — empirically, across a whole
library — whether a native-core change altered playback, fixed something, or regressed it.

It walks a **directory of audio files** you point it at. There is no database, scanner, or library to
set up: drop a corpus folder on the machine, point `--dir` at it, and go.

> This document is written to be followed start-to-finish by an agent on a fresh machine. Commands are
> copy-pasteable; expected output and how to interpret it are spelled out.

---

## 1. What you need

- This repo, buildable (`./gradlew` works).
- A JDK **with JNI headers** (`include/jni.h`) — needed to host-build the native libs. A plain JRE
  won't do. If the resolved JDK lacks headers, pass `-Pchipbox.jvm.nativeJdk=/path/to/jdk`.
- The native toolchain the libs need: `cmake` + a C/C++ compiler + `make` (Linux/macOS host build).
- A **corpus directory** of audio files for the format you're testing, e.g. a tree of `.usf`/
  `.miniusf` files. Chain/library files (`*.usflib`, `*.psflib`, `*_lib.*`) must sit **next to** the
  tracks that reference them — the native loaders resolve them as siblings.

No device, emulator, or audio hardware is required; rendering is offline and deterministic.

---

## 2. The two-pass model (read this first)

`System.loadLibrary` binds **one** version of a `.so` per JVM process — you cannot hold "before" and
"after" of a native core in memory at once. So A/B is two passes against on-disk artifacts:

```
1. render a BASELINE run                     (current native lib)
2. change the native source + rebuild the lib
3. render a CANDIDATE run                     (new native lib)
4. diff baseline vs candidate
```

The `:apps:abrender:run` task depends on `:apps:jvm:nativeLibs`, and the native build is
input-tracked (`cbox/native/<emu>/` → `apps/jvm/libs/lib<emu>.so`). So after you edit a native
source, the next render **rebuilds the changed lib automatically**. To be certain, rebuild
explicitly and check the `.so` timestamp before the candidate pass (step 4 below).

---

## 3. Quick start — USF example

```sh
# (0) From the repo root. Build the current native libs.
./gradlew :apps:jvm:nativeLibs

# (1) BASELINE: one track per game, 30s each, USF only.
./gradlew :apps:abrender:run --args="render --dir /path/to/usf-corpus --label baseline --ext usf,miniusf"

# (2) Apply the native change, e.g. edit files under cbox/native/usf/, then REBUILD + verify:
ls -l --time-style=+%T apps/jvm/libs/libusf.so      # note the time
./gradlew :apps:jvm:nativeEmulatorUsf               # rebuilds just libusf.so
ls -l --time-style=+%T apps/jvm/libs/libusf.so      # time MUST have advanced

# (3) CANDIDATE: identical render args, new label.
./gradlew :apps:abrender:run --args="render --dir /path/to/usf-corpus --label candidate --ext usf,miniusf"

# (4) DIFF.
./gradlew :apps:abrender:run --args="diff --a baseline --b candidate"
```

Per-emulator rebuild tasks (or `nativeLibs` for all):

| backend / `--ext` | rebuild task | lib |
|---|---|---|
| USF (`usf`, `miniusf`) | `nativeEmulatorUsf` | `libusf.so` |
| PSF family (`psf`, `minipsf`, `psf2`, …) | `nativeEmulatorSlopsf` | `libslopsf.so` |
| SSF/DSF (`ssf`, `dsf`) | `nativeEmulatorSsf` | `libssf.so` |
| GME (`spc`, `nsf`, `nsfe`, `gbs`) | `nativeEmulatorGme` | `libgme.so` |
| VGM (`vgm`, `vgz`) | `nativeEmulatorVgm` | `libvgm.so` |
| 2SF (`twosf`, `mini2sf`) | `nativeEmulatorTwosf` | `libtwosf.so` |
| NCSF (`ncsf`, `minincsf`) | `nativeEmulatorNcsf` | `libncsf.so` |
| GBA (`gsf`, `minigsf`) | `nativeEmulatorGba` | `libgba.so` |
| vgmstream (many) | `nativeEmulatorVgmstream` | `libvgmstream.so` |

---

## 4. Commands

### `render --dir <folder> [options]`

Walks `<folder>`, selects tracks, renders each to `<out>/<label>/wav/<name>.wav` and appends a row to
`<out>/<label>/metrics.tsv`.

| option | meaning | default |
|---|---|---|
| `--dir <folder>` | corpus root to walk for audio files | **required** |
| `--label <name>` | run name; output goes to `<out>/<name>/` | `run` |
| `--seconds <n>` | seconds of audio rendered per track | `30` |
| `--ext <a,b>` | only these extensions, e.g. `usf,miniusf` | all supported |
| `--game <substr>` | only files whose **parent folder** name contains `<substr>` | — |
| `--every-song` | render every playable file (default: one per folder) | one per folder |
| `--subsong <n>` | subsong index for multi-song formats (NSF/GBS) | `0` |
| `--limit <n>` | cap the number of tracks (use for a quick smoke run) | — |
| `--overwrite` | re-render tracks already present | resume/skip |
| `--max-wall-seconds <n>` | per-track wall-clock cap | `120` |
| `--out <dir>` | runs root | `ab-runs` |

**Selection.** Corpora are laid out as `.../<Game>/<track>.<ext>`, so the parent folder names the
game and the filename names the track. By default one representative track is rendered per folder
(the alphabetically-first playable file); `--every-song` renders all of them.

### `diff --a <run> --b <run> [options]`

| option | meaning | default |
|---|---|---|
| `--a <run>` | baseline run — a label under `<out>`, or a path to a run dir | **required** |
| `--b <run>` | candidate run | **required** |
| `--top <n>` | how many most-changed tracks to print | `20` |
| `--out <dir>` | runs root (to resolve labels) | `ab-runs` |

---

## 5. Output layout

```
ab-runs/
  baseline/
    metrics.tsv                 # one row per track (see §7)
    wav/<game>__<title>__t<subsong>__<hash>.wav
  candidate/
    metrics.tsv
    wav/...
    diff-vs-baseline.tsv        # written by `diff`, full per-track report
```

`ab-runs/` is git-ignored. The WAVs are normal 16-bit stereo files — to compare a specific track by
ear, open the same filename under `baseline/wav/` and `candidate/wav/`.

---

## 6. Reading the `diff` output

`diff` joins the two runs by track and prints a verdict tally, then likely regressions, then fixes,
then the most-changed tracks with the paths of both WAVs.

**Verdicts**

| verdict | meaning |
|---|---|
| `IDENTICAL` | bit-for-bit equal PCM (hashes match) |
| `CHANGED` | both produced audio, but it differs |
| `REGRESSED_SILENT` | baseline had audio, candidate is silent — **likely regression** |
| `NEW_ERROR` | baseline rendered, candidate failed — **likely regression** |
| `FIXED_ERROR` | baseline failed, candidate rendered — improvement |
| `STILL_ERROR` | both failed |
| `ONLY_IN_A` / `ONLY_IN_B` | track present in only one run (corpus/args mismatch) |

**Metrics**

- **`diffRMS`** (headline) — RMS of the sample-wise difference `A−B`, in dBFS. `-inf` = identical;
  the higher (closer to 0), the more the waveform changed.
- **`ΔRMS`** — change in the track's overall RMS level (candidate minus baseline), in dB.
- **`maxΔ`** — largest single-sample difference (out of 32768).

**How to judge a result**

- **All `IDENTICAL`** → the change had **no effect on output**. This is the expected, passing result
  for a cosmetic / no-op / warning-only change (e.g. a sync phase that only renames symbols).
- **`CHANGED` with very low `diffRMS`** (roughly `< -80 dBFS`, `maxΔ` a few LSB) → numerically
  negligible; almost certainly inaudible. Usually fine.
- **`CHANGED` with higher `diffRMS`** → audibly different. **Listen** to both WAVs (paths are printed)
  and decide whether it's the intended improvement or a regression.
- **`REGRESSED_SILENT` / `NEW_ERROR`** → treat as a regression until proven otherwise; investigate
  the named tracks before accepting the change.
- **`FIXED_ERROR`** → the change fixed a track that previously failed/was silent.

A run where the *only* differences are intended (e.g. a known set of tracks a fix targets) and
everything else is `IDENTICAL` is the ideal "surgical change" signal.

---

## 7. `metrics.tsv` columns

Tab-separated, one row per track: `trackKey, game, title, ext, sampleRate, frames, rmsDbfs,
peakDbfs, lufs, pcmHash, error, wav`. `rmsDbfs`/`peakDbfs` are dBFS (`-inf` = silent), `lufs` is
BS.1770 integrated loudness (`NaN` if under ~400 ms), `pcmHash` is an FNV-1a hash of the rendered
PCM (the bit-identity check), `error` is empty on success.

---

## 8. Running unattended over a large corpus

- **Resumable.** Re-running the same `render` skips tracks already present (matched by `metrics.tsv` +
  an existing WAV). A crashed or interrupted run continues where it left off; `--overwrite` forces a
  full re-render.
- **Isolated failures.** A bad/unloadable track records an `error` row and the run continues — one
  broken file never aborts a 400-game sweep.
- **Wall-clock cap.** `--max-wall-seconds` bounds a backend that produces audio forever-but-slowly. A
  single native call that wedges completely is not interruptible from the JVM (rare in practice).
- **Smoke first.** Add `--limit 5` to validate the corpus, extensions, and chain files before a full
  sweep.

---

## 9. Gotchas

- **Rebuild the native lib between passes.** If you forget, both passes use the same `.so` and `diff`
  reports `IDENTICAL` for everything — a false pass. Verify the `lib<emu>.so` timestamp advanced
  (step 2 in §3).
- **Chain/library files must be present as siblings.** `mini*` formats and PSF chains load a shared
  `*.usflib` / `*.psflib` / `*_lib.*` from the same directory as the track. If the corpus is missing
  them, those tracks fail with a load error.
- **Multi-subsong formats.** Directory mode renders one subsong per file (`--subsong`, default 0).
  Full per-subsong enumeration (NSF/GBS) isn't supported here. One-file-per-song formats (USF, the
  whole PSF family, SPC, VGM) are unaffected.
- **Determinism.** Rendering uses a fixed `--seconds` of raw samples with no fade and no
  declared-length budget, so output is reproducible and comparable across builds. Keep `--seconds`,
  `--dir`, and `--ext` identical between the two passes.
- **Game grouping.** "One per game" means one file per leaf folder. If your corpus is flat (one file
  per game directly under `--dir`), use `--every-song`.
