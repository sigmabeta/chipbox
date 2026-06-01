package net.sigmabeta.chipbox.abrender

import java.io.File

/**
 * Parsed invocation. One [Args] drives either `render` (most fields) or `diff` ([runA]/[runB]).
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
    val runA: String?,
    val runB: String?,
    val topN: Int,
)
