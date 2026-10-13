package net.sigmabeta.chipbox.perfbench

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One perfbench run: the fingerprint-scoped, corpus-pinned document written as `results.json`.
 *
 * One file carries every axis measured by a single invocation, keyed by axis name under [axes]
 * (e.g. `"audio"`). Results are never merged across runs — the file is the unit that a baseline
 * diff compares against, so the top-level [machine] and [corpus] blocks apply to all axes in it.
 */
@Serializable
data class PerfResult(
    val generated: String,
    @SerialName("chipbox_commit") val chipboxCommit: String,
    @SerialName("sage_commit") val sageCommit: String,
    val config: PerfConfig,
    val machine: Fingerprint,
    val corpus: CorpusRef,
    val axes: Map<String, Axis>,
)

/** Run-wide inputs — the knobs shared by every axis in one [PerfResult]. */
@Serializable
data class PerfConfig(
    val repeat: Int,
    val scenarios: List<String>,
)

/**
 * The corpus actually measured: a reference to its root plus, once populated, the ordered manifest
 * of `{path, sha256, bytes}` for every file read. Two runs are only comparable when their manifests
 * match; [snapshotHash] names the library snapshot a device run was seeded from, when there is one.
 */
@Serializable
data class CorpusRef(
    val root: String,
    val manifest: List<CorpusEntry> = emptyList(),
    @SerialName("snapshot_hash") val snapshotHash: String? = null,
)

/** One corpus file: its path relative to the corpus root, its content hash, and its size. */
@Serializable
data class CorpusEntry(
    val path: String,
    val sha256: String,
    val bytes: Long,
)

/** A named axis of measurement. Sealed so each axis keeps its own result shape in the JSON. */
@Serializable
sealed interface Axis

/** Audio emulation throughput — one [AudioResult] per scenario (emulator family). */
@Serializable
@SerialName("audio")
data class AudioAxis(
    val config: AudioAxisConfig,
    val results: List<AudioResult>,
    val score: Double? = null,
) : Axis

/** The fixed sample wall and repeat count an audio axis was measured with. */
@Serializable
data class AudioAxisConfig(
    @SerialName("sample_seconds") val sampleSeconds: Int,
    val repeats: Int,
)

/**
 * One audio scenario's measurement.
 *
 * [rtf] is the machine-independent headline: the rendered audio duration divided by the median
 * wall-clock render time. [median]/[min]/[max] and [samples] describe the wall-clock seconds
 * across the timed repeats; [cpuSeconds] is the median thread CPU time, a secondary metric that
 * shows how much of the wall time was actual compute (`null` when the JVM can't report CPU time).
 */
@Serializable
data class AudioResult(
    val scenario: String,
    val rtf: Double,
    @SerialName("cpu_s") val cpuSeconds: Double?,
    val median: Double,
    val min: Double,
    val max: Double,
    val samples: List<Double>,
)

/** UI rendering/jank — one [UiResult] per scenario (e.g. the BrowseByGame scroll). */
@Serializable
@SerialName("ui")
data class UiAxis(
    val config: UiAxisConfig,
    val results: List<UiResult>,
    val score: Double? = null,
) : Axis

/** The iteration count a UI axis was measured with. */
@Serializable
data class UiAxisConfig(
    val iterations: Int,
)

/**
 * One UI scenario's measurement.
 *
 * [frameP50Ms]/[frameP90Ms]/[frameP99Ms] are percentiles of the pooled frame CPU durations across
 * iterations — the frame-time distribution a jank investigation reads. [jankPercent] is the share
 * of frames that missed their deadline (a positive `frameOverrunMs`), `null` on devices that can't
 * report frame overrun. [traceSections] carries the labelled trace sums the scenario was told to
 * watch (`GridImage`, `CrossfadeImage`), so a regression in the work *behind* a frame is visible
 * next to the frame-time percentiles.
 */
@Serializable
data class UiResult(
    val scenario: String,
    val frames: Int,
    @SerialName("frame_p50_ms") val frameP50Ms: Double,
    @SerialName("frame_p90_ms") val frameP90Ms: Double,
    @SerialName("frame_p99_ms") val frameP99Ms: Double,
    @SerialName("jank_percent") val jankPercent: Double? = null,
    @SerialName("trace_sections") val traceSections: List<TraceSectionSum> = emptyList(),
)

/** One labelled trace section's pooled work: total time in the section and its instance count. */
@Serializable
data class TraceSectionSum(
    val name: String,
    @SerialName("sum_ms") val sumMs: Double,
    val count: Int,
)
