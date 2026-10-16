# abrender-core — shared A/B render engine (+ on-device arm64 harness)

The render engine behind `:testing:abrender`, factored into a KMP module so the **same code renders on
both the host JVM and a real Android device**. The desktop CLI proves the x86_64 emulator cores; the
instrumented test here proves the **arm64** ones. Output is byte-for-byte the same shape, so a device
run and a host run are compared by the same code — the comparison always runs host-side.

It is also the raw-core render engine the perfbench **device audio axis** times (see
[`:benchmark`](../../benchmark/README.md)): that module depends on this one as a KMP consumer, so the
same arm64 `:native` cores land in its test APK and its RTF results are the phone's ground truth.

## Layout

- `src/main/java/` (`jvmSharedMain`) — the engine: `EmulatorBank`/`Renderer` (drives each emulator's
  `*Internal` JNI entry points straight to PCM, no Speaker), `Metrics` (RMS/peak/LUFS/FNV hash),
  `WavFile`, `CorpusWalker`, `RenderCommand` (`runRender`), `RunDiff` (the sample-wise `diff`),
  `Args`, and `GoldenCheck` (`GoldenReport`/`GoldenTolerance` — the pass/fail semantics `diff --check`
  and verify.sh act on). Pure `java.*` — no Android imports — so it compiles unchanged for both
  targets. `:testing:abrender` adds only the CLI shell (`Main`, `DiffCommand`).
- `src/androidDeviceTest/kotlin/` — `DeviceRenderTest`, an `androidx.test` instrumentation that runs
  `runRender` on-device against a corpus pushed to its external files dir and writes the run back there
  for `adb pull`. There is no on-device comparison test: the host diffs the pulled run.
- Harness scripts live in **`scripts/abrender/`**: `record.sh` (render a corpus on host or device) and
  `verify.sh` (render again + `diff --check` against a recorded run). See
  [`testing/abrender/README.md`](../abrender/README.md).

## Why KMP (not a plain JVM module)

Only a KMP consumer resolves the **android** variant of each emulator `:real`, whose `androidMain`
carries `runtimeOnly(:native)` — that's what packages the arm64 `.so` into the self-instrumenting
device-test APK. A plain `sage.jvm` module would pull the JVM variants and ship no `.so`. The
device-test APK is self-contained (all nine arm64 cores), so there's no app under test to install.

## Run it

The short version (a `--where device` render, pulled into the shared run store):

```sh
scripts/abrender/record.sh --where device --corpus /path/to/corpus --ext usf,miniusf
scripts/abrender/verify.sh --where device --corpus /path/to/corpus   # vs the recorded reference
```

Direct gradle/adb equivalents, if you don't want the script:

```sh
./gradlew :testing:abrender-core:installAndroidDeviceTest
adb push <corpus>/. /sdcard/Android/data/net.sigmabeta.chipbox.abrender.test/files/abrender/in
adb shell am instrument -w -r \
  -e ext usf,miniusf -e seconds 30 -e label reference \
  net.sigmabeta.chipbox.abrender.test/androidx.test.runner.AndroidJUnitRunner
adb pull /sdcard/Android/data/net.sigmabeta.chipbox.abrender.test/files/abrender/out/reference ./ab-runs/
```

Instrumentation args (`-e <key> <value>`) mirror the desktop `render` flags: `dir`, `out`, `label`,
`seconds`, `ext`, `game`, `every-song`, `subsong`, `limit`, `overwrite`, `max-wall-seconds`, `shards`,
`shard`. Paths default to `<externalFiles>/abrender/{in,out}`.

## The comparison and tolerance

`RunDiff` compares two runs sample-for-sample and `GoldenCheck` reduces the rows to a verdict. `diff
--check` fails on any track worse than the tolerance, and `verify.sh` picks the tolerance:
`GoldenTolerance.STRICT` (bit-identity) by default, or `CROSS_ISA` (`maxΔ ≤ 1`, diff-RMS ≤ −80 dBFS)
when the reference and the fresh render came from different places. The cross-ISA allowance exists
because a core that resamples through a `double` stage (mGBA's GBA sinc resampler) drifts ±1 LSB across
libms — `sin`/`cos` differ between glibc and Bionic; anything larger still fails.
