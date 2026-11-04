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
    val sampleRate: Int,
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

/**
 * One track on the **uncached** path: repeated full opens of a live `EmulatorPcmSource`, pooled to
 * median / min / max per-open wall time. [frames] is the first open's drained length.
 */
internal data class UncachedRow(
    val path: String,
    val frames: Long,
    val sampleRate: Int,
    val opens: Int,
    val wallMedianSeconds: Double,
    val wallMinSeconds: Double,
    val wallMaxSeconds: Double,
    val firstFrameMedianSeconds: Double,
    val loadsPerOpen: Double,
    val teardownsPerOpen: Double,
    val sourceClass: String,
)

/** Reduces [phases]' repeated uncached opens to one row. */
internal fun uncachedRow(path: String, phases: List<CachePhase>, frames: Long): UncachedRow {
    require(phases.isNotEmpty()) { "at least one uncached open is required" }
    return UncachedRow(
        path = path,
        frames = frames,
        sampleRate = phases.first().sampleRate,
        opens = phases.size,
        wallMedianSeconds = median(phases.map { it.wallSeconds }),
        wallMinSeconds = phases.minOf { it.wallSeconds },
        wallMaxSeconds = phases.maxOf { it.wallSeconds },
        firstFrameMedianSeconds = median(phases.map { it.firstFrameSeconds }),
        loadsPerOpen = phases.sumOf { it.emulatorLoads }.toDouble() / phases.size,
        teardownsPerOpen = phases.sumOf { it.emulatorTeardowns }.toDouble() / phases.size,
        sourceClass = phases.first().sourceClass,
    )
}
