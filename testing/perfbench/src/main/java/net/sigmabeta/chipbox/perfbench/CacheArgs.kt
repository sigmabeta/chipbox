package net.sigmabeta.chipbox.perfbench

import java.io.File

internal const val DEFAULT_CACHE_OUT = "build/perfbench/cache"
internal const val DEFAULT_CACHE_SECONDS = 30

/**
 * Parsed `perfbench cache` invocation. The command drives the **production** PCM cache factories,
 * not the raw emulator loop the `audio` axis uses: by default the cold render-through and warm
 * cache hit of `RealPcmTrackSourceFactory`, or with [uncached] the live `EmulatorPcmSource` path of
 * `UncachedPcmTrackSourceFactory` (the "cache bypassed" regression axis).
 */
data class CacheArgs(
    val corpusDir: File,
    val filter: String?,
    val repeat: Int,
    val outDir: File,
    val sampleSeconds: Int,
    val uncached: Boolean,
)

/**
 * Turns parsed `--key value` options into a [CacheArgs], applying defaults and rejecting missing or
 * invalid input up front (before any native library is loaded). `--seconds` bounds how much audio
 * each open renders; `--repeat` is the number of timed opens per track (warm cache hits, or
 * repeated uncached opens with `--uncached`).
 */
fun parseCacheOptions(options: Map<String, String>): CacheArgs {
    val corpusDir = parseCorpusDir(options)

    val repeat = intOption(options, "repeat") ?: DEFAULT_REPEAT
    require(repeat >= 1) { "--repeat must be >= 1 (was $repeat)" }

    val sampleSeconds = intOption(options, "seconds") ?: DEFAULT_CACHE_SECONDS
    require(sampleSeconds >= 1) { "--seconds must be >= 1 (was $sampleSeconds)" }

    val filter = options["filter"]
    filter?.let(::requireValidGlob)

    return CacheArgs(
        corpusDir = corpusDir,
        filter = filter,
        repeat = repeat,
        outDir = File(options["out"] ?: DEFAULT_CACHE_OUT),
        sampleSeconds = sampleSeconds,
        uncached = options.containsKey("uncached"),
    )
}
