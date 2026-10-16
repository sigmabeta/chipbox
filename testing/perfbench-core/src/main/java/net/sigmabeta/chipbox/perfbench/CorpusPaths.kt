package net.sigmabeta.chipbox.perfbench

import java.io.File

/**
 * The `/`-separated path of [path] relative to the corpus [root] — the portable identity a corpus
 * file carries through the result JSON and the corpus manifest. Always `/`-separated so one corpus
 * is comparable across host and device, whatever their native separators.
 *
 * Shared by the host audio axis (which walks files) and the device audio axis (whose walker
 * carries paths as text), so both key a result by the same string.
 */
fun corpusRelativePath(root: File, path: File): String =
    path.relativeToOrSelf(root).invariantSeparatorsPath

/** [corpusRelativePath] from a path string, for a corpus walker that carries paths as text. */
fun corpusRelativePath(root: File, path: String): String = corpusRelativePath(root, File(path))

/**
 * A stable, filesystem-safe scratch WAV file name derived from a file's `/`-separated
 * [relativePath]. Hashing the path keeps distinct corpus paths from colliding, so a run can reuse
 * one scratch directory without a track overwriting another's output; the `.wav` suffix is included
 * so callers hand it straight to [File].
 */
fun scratchWavName(relativePath: String): String =
    "${relativePath.hashCode().toUInt().toString(SCRATCH_WAV_RADIX)}.wav"

private const val SCRATCH_WAV_RADIX = 16
