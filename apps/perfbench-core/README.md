# perfbench-core — the shared performance-harness contract

The single new seam of the perfbench work: the serialized contract both homes of
the harness share. The JVM CLI [`apps/perfbench`](../perfbench/README.md) writes
it for host axes; the Android `:benchmark` module writes it for device axes.
Keeping the shape here means a result written by one home can be read by the
other.

It is **pure contract** — no renderer, no I/O, no platform code beyond the
optional host probe below. KMP (`androidTarget` + `jvm`) so both homes resolve a
variant.

The **library-snapshot** DTO, its JSON dialect, and the `importLibrarySnapshot`
capability are a repository concern and live in
[`:cbox:common:repository:api`](../../cbox/common/repository/api/README.md) —
not here — so the app's Settings feature (which surfaces ingest) depends on the
repository `:api`, not on this harness module.

## Contents

| file | role |
|---|---|
| `PerfResult.kt` | the `results.json` schema: `PerfResult`, `PerfConfig`, `CorpusRef`/`CorpusEntry`, and the sealed `Axis` hierarchy (`AudioAxis` + `AudioResult`) |
| `Fingerprint.kt` | sealed `Fingerprint` (`HostFingerprint`, `DeviceFingerprint`) and the pure `hostFingerprint(environment, commits)` builder |
| `AudioScenario.kt` | the canonical audio scenario ids (`audio.usf`, `audio.vgm`, `audio.psf`) and the extensions each selects |
| `AudioReport.kt` | pure report math: `audioResult(AudioMeasurement)` and `median` |
| `Diff.kt` | the baseline-diff contract: `compareAudioRuns`/`DiffOutcome`, the `DiffRefusal` comparability guards, the `FingerprintClass` of a `Fingerprint`, `ScenarioSpeedup`/`DiffReport`, and `geometricMean` |
| `PerfbenchJson.kt` | the one JSON dialect for `results.json` (`class` discriminator, pretty-printed). The library snapshot has its own dialect (`LibrarySnapshotJson`) in `:cbox:common:repository:api`. |
| `SystemHostEnvironment.kt` | `jvmMain` only: probes the running host for the facts a host fingerprint records (`/proc/cpuinfo`, `/etc/os-release`, `java.*`) |

## Using it

```kotlin
// Build a host result's fingerprint.
val machine = hostFingerprint(SystemHostEnvironment.detect(), chipboxCommit, sageCommit)

// Reduce timed repeats to one scenario result.
val result = audioResult(
    AudioMeasurement(
        scenario = AudioScenario.USF.id,
        frames = 44_100L * 30,
        sampleRate = 44_100,
        wallSeconds = listOf(1.25, 1.21, 1.23),
        cpuSeconds = listOf(1.10, 1.09, 1.11),
    ),
)

// Write / read with the shared dialect.
val json = PerfbenchJson.encodeToString(perfResult)
val back = PerfbenchJson.decodeFromString<PerfResult>(json)

// Compare two runs; a refusal means they aren't comparable (class / scenario set / corpus).
when (val outcome = compareAudioRuns(baseline, current)) {
    is DiffOutcome.Compared -> println(outcome.report.geometricMean)
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
math, scenario ids, and the diff comparability guards and geometric mean) and a
`jvmTest` for the `/proc/cpuinfo` parser. The snapshot's own round-trip + path
transforms and the ingest capability are tested in
`:cbox:common:repository:api`/`:real`; the scanner → snapshot → import round trip
end to end is covered in `apps/perfbench`.
