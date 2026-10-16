package net.sigmabeta.chipbox.abrender

import java.io.File

/**
 * Parsed invocation. One [Args] drives either `render` (most fields) or `diff` ([runA]/[runB],
 * with the optional gate [check]/[tolerance]).
 */
class Args(
    val command: String,
    val label: String,
    val seconds: Int,
    val outDir: File,
    val corpusDir: File,
    val extensions: Set<String>?,
    val gameFilter: String?,
    val everySong: Boolean,
    val subsong: Int,
    val limit: Int?,
    val overwrite: Boolean,
    val maxWallMillis: Long,
    val shards: Int,
    val shard: Int,
    val runA: String?,
    val runB: String?,
    val topN: Int,
    /** `diff --check`: tolerance name (`strict` or `cross-isa`) a divergence is judged against. */
    val tolerance: String?,
    /** `diff --check`: exit non-zero if any track fails [tolerance] (the verify gate). */
    val check: Boolean,
)
