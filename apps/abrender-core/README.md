# abrender-core — shared A/B render engine (+ on-device arm64 harness)

The render engine behind `:apps:abrender`, factored into a KMP module so the **same code renders on
both the host JVM and a real Android device**. The desktop CLI proves the x86_64 emulator cores; the
instrumented test here proves the **arm64** ones. Output is byte-for-byte the same shape, so the
desktop `abrender diff` compares a device run against a host run with no platform-specific code.

## Layout

- `src/main/java/` (`jvmSharedMain`) — the engine: `EmulatorBank`/`Renderer` (drives each emulator's
  `*Internal` JNI entry points straight to PCM, no Speaker), `Metrics` (RMS/peak/LUFS/FNV hash),
  `WavFile`, `CorpusWalker`, `RenderCommand` (`runRender`). Pure `java.*` — no Android imports — so it
  compiles unchanged for both targets. `:apps:abrender` adds only the CLI shell (`Main`, `DiffCommand`).
- `src/androidDeviceTest/kotlin/` — `DeviceRenderTest`, an `androidx.test` instrumentation that runs
  `runRender` on-device against a corpus pushed to its external files dir.
- `run-on-device.sh` — the adb harness: build+install, push corpus, `am instrument`, pull the run.

## Why KMP (not a plain JVM module)

Only a KMP consumer resolves the **android** variant of each emulator `:real`, whose `androidMain`
carries `runtimeOnly(:native)` — that's what packages the arm64 `.so` into the self-instrumenting
device-test APK. A plain `sage.jvm` module would pull the JVM variants and ship no `.so`. The
device-test APK is self-contained (all nine arm64 cores), so there's no app under test to install.

## Run it

See **§10 of `apps/abrender/README.md`** for the full cross-ISA A/B workflow. The short version:

```sh
apps/abrender-core/run-on-device.sh --dir /path/to/corpus --label arm64 --ext usf,miniusf
./gradlew :apps:abrender:run --args="diff --a <x86_64-run> --b ./ab-runs-device/arm64"
```

Direct gradle/adb equivalents, if you don't want the script:

```sh
./gradlew :apps:abrender-core:installAndroidDeviceTest
adb push <corpus>/. /sdcard/Android/data/net.sigmabeta.chipbox.abrender.test/files/abrender/in
adb shell am instrument -w -r \
  -e ext usf,miniusf -e seconds 30 -e label arm64 \
  net.sigmabeta.chipbox.abrender.test/androidx.test.runner.AndroidJUnitRunner
adb pull /sdcard/Android/data/net.sigmabeta.chipbox.abrender.test/files/abrender/out/arm64 ./ab-runs-device/
```

Instrumentation args (`-e <key> <value>`) mirror the desktop `render` flags: `dir`, `out`, `label`,
`seconds`, `ext`, `game`, `every-song`, `subsong`, `limit`, `overwrite`, `max-wall-seconds`, `shards`,
`shard`. Paths default to `<externalFiles>/abrender/{in,out}`.
