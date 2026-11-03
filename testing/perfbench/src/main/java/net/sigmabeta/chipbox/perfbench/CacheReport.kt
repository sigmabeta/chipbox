package net.sigmabeta.chipbox.perfbench

/**
 * One timed phase of the production cache path: `Factory.open()` plus a full read to end-of-track.
 * [firstFrameSeconds] is time-to-first-frame measured from before `open()`, so it includes staging,
 * the factory's emulator construction, and (on a warm hit) whatever work the factory does before it
 * discovers the cache file. [emulatorLoads] / [emulatorTeardowns] count native track loads and
 * teardowns observed through a counting decorator around the real backend.
 */
internal data class CachePhase(
    val wallSeconds: Double,
    val firstFrameSeconds: Double,
    val emulatorLoads: Int,
    val emulatorTeardowns: Int,
    val sourceClass: String,
)

/** One track's cold + warm cache measurements. A failed track carries [error] and no phases. */
internal data class CacheMeasurement(
    val path: String,
    val frames: Long,
    val sampleRate: Int,
    val cold: CachePhase,
    val warm: List<CachePhase>,
    val error: String? = null,
)

/** A [CacheMeasurement] reduced to the numbers a baseline table cites, with warm opens pooled. */
internal data class CacheRow(
    val path: String,
    val frames: Long,
    val sampleRate: Int,
    val coldWallSeconds: Double,
    val coldFirstFrameSeconds: Double,
    val coldEmulatorLoads: Double,
    val coldEmulatorTeardowns: Double,
    val coldSourceClass: String,
    val warmOpens: Int,
    val warmWallMedianSeconds: Double,
    val warmWallMinSeconds: Double,
    val warmWallMaxSeconds: Double,
    val warmFirstFrameMedianSeconds: Double,
    val warmEmulatorLoadsPerOpen: Double,
    val warmEmulatorTeardownsPerOpen: Double,
    val warmSourceClass: String,
)

/** Reduces [measurement]'s warm repeats to medians and per-open native-emulator counts. */
internal fun cacheRow(measurement: CacheMeasurement): CacheRow {
    require(measurement.warm.isNotEmpty()) { "at least one warm open is required" }
    val warm = measurement.warm
    return CacheRow(
        path = measurement.path,
        frames = measurement.frames,
        sampleRate = measurement.sampleRate,
        coldWallSeconds = measurement.cold.wallSeconds,
        coldFirstFrameSeconds = measurement.cold.firstFrameSeconds,
        coldEmulatorLoads = measurement.cold.emulatorLoads.toDouble(),
        coldEmulatorTeardowns = measurement.cold.emulatorTeardowns.toDouble(),
        coldSourceClass = measurement.cold.sourceClass,
        warmOpens = warm.size,
        warmWallMedianSeconds = median(warm.map { it.wallSeconds }),
        warmWallMinSeconds = warm.minOf { it.wallSeconds },
        warmWallMaxSeconds = warm.maxOf { it.wallSeconds },
        warmFirstFrameMedianSeconds = median(warm.map { it.firstFrameSeconds }),
        warmEmulatorLoadsPerOpen = warm.sumOf { it.emulatorLoads }.toDouble() / warm.size,
        warmEmulatorTeardownsPerOpen = warm.sumOf { it.emulatorTeardowns }.toDouble() / warm.size,
        warmSourceClass = warm.first().sourceClass,
    )
}
