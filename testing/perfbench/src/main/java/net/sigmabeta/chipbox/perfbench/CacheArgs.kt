package net.sigmabeta.chipbox.perfbench

import java.io.File

internal const val DEFAULT_CACHE_OUT = "build/perfbench/cache"
internal const val DEFAULT_CACHE_SECONDS = 30

/**
 * Parsed `perfbench cache` invocation. The command drives the **production** PCM cache factory
 * (`RealPcmTrackSourceFactory`), not the raw emulator loop the `audio` axis uses, so both halves of
 * playback — cold render-through and warm cache hit — are measured on the real path.
 */
data class CacheArgs(
    val corpusDir: File,
    val filter: String?,
    val repeat: Int,
    val outDir: File,
    val sampleSeconds: Int,
)

/**
 * Turns parsed `--key value` options into a [CacheArgs], applying defaults and rejecting missing or
 * invalid input up front (before any native library is loaded). `--seconds` bounds how much audio
 * the cold pass renders into the cache; `--repeat` is the number of warm opens timed per track.
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
    )
}
