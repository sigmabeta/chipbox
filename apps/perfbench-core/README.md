# perfbench-core — the shared performance-harness contract

The single new seam of the perfbench work: the serialized contract both homes of
the harness share. The JVM CLI [`apps/perfbench`](../perfbench/README.md) writes
it for host axes; the Android `:benchmark` module writes it for device axes.
Keeping the shape here means a result written by one home can be read by the
other.

It is **contract-first** — no renderer, and no I/O beyond the narrow platform
helpers below (the optional host probe, the shared corpus-file hash, and the
corpus-manifest / pin builders). KMP (`androidTarget` + `jvm`) so both homes
resolve a variant. `commonMain` stays `java.*`-free; code needing it lives in
`src/main/java` (the `jvmSharedMain` source set both targets depend on).

The **library-snapshot** DTO, its JSON dialect, and the `importLibrarySnapshot`
capability are a repository concern and live in
[`:cbox:common:repository:api`](../../cbox/common/repository/api/README.md) —
not here — so the app's Settings feature (which surfaces ingest) depends on the
repository `:api`, not on this harness module.

## Contents

| file | role |
|---|---|
| `PerfResult.kt` | the `results.json` schema: `PerfResult`, `PerfConfig`, `CorpusRef`/`CorpusEntry`, and the sealed `Axis` hierarchy (`AudioAxis` + `AudioResult`, `UiAxis` + `UiResult`, `StartupAxis` + `StartupResult`) |
| `ResultArtifact.kt` | where a run's result is written: `RESULTS_FILE_NAME` (the whole-run `results.json`) and `axisResultFileName(axis)` (the distinct `results-<axis>.json` a device test writes, so no two axes in a run overwrite each other) |
| `Fingerprint.kt` | sealed `Fingerprint` (`HostFingerprint`, `DeviceFingerprint`) and the pure `hostFingerprint` / `deviceFingerprint` builders (from `HostEnvironment` / `DeviceEnvironment`) |
| `UiScenario.kt` | the canonical UI scenario ids (`ui.browse_by_game_scroll`, `ui.home_scroll`, `ui.browse_all_tracks_scroll`, `ui.artist_detail_scroll`) |
| `StartupScenario.kt` | the canonical startup scenario ids (`startup.cold`, `startup.warm`) |
| `AudioReport.kt` | pure report math: `audioResult(AudioMeasurement)`, `erroredAudioResult(path, error)`, and `median` |
| `UiReport.kt` | pure report math for the UI axis: `uiResult(UiMeasurement)` plus `percentile` and `jankPercent` |
| `StartupReport.kt` | pure report math for the startup axis: `startupResult(StartupMeasurement)` (time-to-first-content percentiles) |
| `DeviceBenchmarkLayout.kt` | the fixed on-device layout (`Music/chipbox-test/` under shared external storage holding the pushed snapshot + corpus) shared by the app seed and the macrobenchmark |
| `Diff.kt` | the baseline-diff contract: `compareRuns`/`DiffOutcome`, the `DiffRefusal` comparability guards, the `FingerprintClass` of a `Fingerprint`, the per-axis `ScenarioComparison`/`AxisComparison`/`DiffReport` (each ratio derived from its axis's primary metric), and `geometricMean` |
| `PerfbenchJson.kt` | the one JSON dialect for `results.json` (`class` discriminator, pretty-printed). The library snapshot has its own dialect (`LibrarySnapshotJson`) in `:cbox:common:repository:api`. |
| `SystemHostEnvironment.kt` | `jvmMain` only: probes the running host for the facts a host fingerprint records (`/proc/cpuinfo`, `/etc/os-release`, `java.*`) |
| `Sha256.kt` | `jvmSharedMain` (`src/main/java`): the one streamed SHA-256 → lowercase-hex file hash, shared by the host corpus-manifest builder and the app's benchmark library seed so a file hashes identically through either |
| `CorpusManifest.kt` | `jvmSharedMain`: `corpusManifest(root, files)` — the ordered `{path, sha256, bytes}` manifest the host audio axis and the device corpus pin are both built from |
| `CorpusPaths.kt` | `jvmSharedMain`: `corpusRelativePath(root, path)` — a file's `/`-separated corpus-relative path (the shared result key) — and `scratchWavName(relativePath)`, the stable scratch-WAV name both audio axes reuse |
| `CorpusPin.kt` | `jvmSharedMain`: `corpusPin(corpusRoot, snapshotFile)` — the corpus block a device run records: the manifest of the whole pushed corpus plus the SHA-256 of the snapshot it was seeded from |

## Using it

```kotlin
// Build a host result's fingerprint.
val machine = hostFingerprint(SystemHostEnvironment.detect(), chipboxCommit, sageCommit)

// Reduce a file's timed repeats to one result, keyed by its corpus-relative path.
val result = audioResult(
    AudioMeasurement(
        path = "Chrono Trigger/track.spc",
        frames = 44_100L * 30,
        sampleRate = 44_100,
        wallSeconds = listOf(1.25, 1.21, 1.23),
        cpuSeconds = listOf(1.10, 1.09, 1.11),
    ),
)

// A file that failed to render becomes a result carrying the error and no real-time factor.
val errored = erroredAudioResult("Chrono Trigger/broken.spc", error = "no audio produced")

// Reduce a device scroll's pooled frames + trace sums to one UI scenario result.
val ui = uiResult(
    UiMeasurement(
        scenario = UiScenario.BROWSE_BY_GAME_SCROLL.id,
        frameCpuMillis = listOf(8.0, 9.5, 12.0, 7.0),
        frameOverrunMillis = listOf(-2.0, 1.0, -3.0, -1.0),
        traceSections = listOf(TraceSectionMeasurement("GridImage", sumMs = 120.0, count = 240)),
    ),
)

// Reduce a device startup's per-launch times to one scenario result (time-to-first-content).
val startup = startupResult(
    StartupMeasurement(
        scenario = StartupScenario.COLD.id,
        timeToFirstContentMillis = listOf(320.0, 305.0, 412.0, 298.0, 331.0),
    ),
)

// Write / read with the shared dialect.
val json = PerfbenchJson.encodeToString(perfResult)
val back = PerfbenchJson.decodeFromString<PerfResult>(json)

// Compare every axis both runs share; a refusal means they aren't comparable (class / scenario set /
// corpus). Each axis's ratios come from its own primary metric (audio render wall median, UI frame
// p50, startup time-to-first-content p50), summarised per axis and overall.
when (val outcome = compareRuns(baseline, current)) {
    is DiffOutcome.Compared -> outcome.report.axes.forEach { println("${it.axis}: ${it.geometricMean}") }
    is DiffOutcome.Refused -> println(outcome.reason.message)
}

// A library snapshot (from :cbox:common:repository:api): raw scan output stored with
// corpus-relative paths, rebased on import.
val snapshot = librarySnapshot(
    root = "/host/corpus",
    scannerVersion = scannerVersion,
    readerVersions = mapOf("psf" to 1),
    games = rawGames,
)
val forDevice = snapshot.games.map { it.rebased("/sdcard/corpus") }

// Ingest a snapshot into the library (rebased onto wherever the corpus lives here). Idempotent and
// with no file-existence check; importing the same snapshot twice is safe.
val result = repository.importLibrarySnapshot(snapshot, root = "/sdcard/corpus")
println("${result.added} added, ${result.updated} updated, ${result.unchanged} unchanged")
```

`libs.kotlinx.serialization.json` is an `api` dependency because the schema types
carry generated serializers that any consumer needs resolvable.

Tested by `commonTest` (schema round-trips, fingerprint construction, report
math for the audio, UI, and startup axes, scenario ids, and the diff
comparability guards, per-axis ratios, and geometric means) and a `jvmTest` for
the `/proc/cpuinfo` parser, the streamed SHA-256 file hash (against a known-good
literal), the shared corpus path / scratch-name helpers, and the corpus pin —
the `corpusManifest` construction it is built from
and the `corpusPin` assembly, including its refusal of a missing corpus root or
snapshot. The manifest's equality semantics (two corpora of the same files are
equal; a difference in content, size, or ordering is not), the CLI arg parsers,
and the scanner → snapshot → import round trip are covered by
[`:apps:perfbench`](../perfbench/README.md)'s own tests. The snapshot's own
round-trip + path transforms and the ingest capability are tested in
`:cbox:common:repository:api`/`:real`; the device axes' mapping
(`BenchmarkData.TestResult` → `UiMeasurement`/`StartupMeasurement`) lives with
the macrobenchmark in `:benchmark`; only the pure report math above is
unit-tested here.
