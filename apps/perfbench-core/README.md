# perfbench-core — the shared performance-harness contract

The single new seam of the perfbench work: the serialized contract both homes of
the harness share. The JVM CLI [`apps/perfbench`](../perfbench/README.md) writes
it for host axes; the Android `:benchmark` module writes it for device axes.
Keeping the shape here means a result written by one home can be read by the
other.

It is **pure contract** — no renderer, no I/O, no platform code beyond the
optional host probe below. KMP (`androidTarget` + `jvm`) so both homes resolve a
variant.

## Contents

| file | role |
|---|---|
| `PerfResult.kt` | the `results.json` schema: `PerfResult`, `PerfConfig`, `CorpusRef`/`CorpusEntry`, and the sealed `Axis` hierarchy (`AudioAxis` + `AudioResult`) |
| `Fingerprint.kt` | sealed `Fingerprint` (`HostFingerprint`, `DeviceFingerprint`) and the pure `hostFingerprint(environment, commits)` builder |
| `AudioScenario.kt` | the canonical audio scenario ids (`audio.usf`, `audio.vgm`, `audio.psf`) and the extensions each selects |
| `AudioReport.kt` | pure report math: `audioResult(AudioMeasurement)` and `median` |
| `Diff.kt` | the baseline-diff contract: `compareAudioRuns`/`DiffOutcome`, the `DiffRefusal` comparability guards, the `FingerprintClass` of a `Fingerprint`, `ScenarioSpeedup`/`DiffReport`, and `geometricMean` |
| `LibrarySnapshot.kt` | the `snapshot` subcommand's `LibrarySnapshot` DTO — the raw scan output (`List<RawGame>`) plus scan metadata (`scanner_version`, `reader_versions`, the corpus `root`), and the pure `relativized`/`rebased` path transforms that make one snapshot portable across hosts and devices |
| `PerfbenchJson.kt` | the one JSON dialect (`class` discriminator, pretty-printed) |
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

// A library snapshot: raw scan output stored with corpus-relative paths, rebased on import.
val snapshot = librarySnapshot(
    root = "/host/corpus",
    scannerVersion = scannerVersion,
    readerVersions = mapOf("psf" to 1),
    games = rawGames,
)
val forDevice = snapshot.games.map { it.rebased("/sdcard/corpus") }
```

`libs.kotlinx.serialization.json` is an `api` dependency because the schema types
carry generated serializers that any consumer needs resolvable. The snapshot DTO
reuses `:cbox:common:repository:api`'s `@Serializable` raw scan types (`RawGame`/
`RawTrack`) as its payload, so that module is an `api` dependency too.

Tested by `commonTest` (schema round-trips, fingerprint construction, report
math, scenario ids, the diff comparability guards and geometric mean, and the
snapshot round-trip + path transforms) and a `jvmTest` for the `/proc/cpuinfo`
parser.
