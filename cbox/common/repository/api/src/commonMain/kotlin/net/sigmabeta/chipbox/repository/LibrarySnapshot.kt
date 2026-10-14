package net.sigmabeta.chipbox.repository

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A **library snapshot** — the serialized result of scanning a corpus once, written by
 * `perfbench snapshot` and ingested by the app without rescanning. It carries the scanner's raw
 * game/track output ([RawGame]/[RawTrack]) rather than the read-side display models, so an import
 * preserves the scan metadata (folder signature, chain files, scanner/reader versions) that a
 * display model drops.
 *
 * Every stored path is **relative to [root]** (see [relativized]), so one snapshot is valid on any
 * host or device: import rebases the relative paths onto wherever the corpus lives ([rebased]).
 *
 * It lives in the repository `:api` because ingest is a repository capability — see
 * [importLibrarySnapshot].
 */
@Serializable
data class LibrarySnapshot(
    @SerialName("format_version") val formatVersion: Int = FORMAT_VERSION,
    @SerialName("scanner_version") val scannerVersion: Int,
    @SerialName("reader_versions") val readerVersions: Map<String, Int> = emptyMap(),
    // The corpus root the snapshot was generated from, recorded for reference. Import ignores it and
    // rebases onto the importing machine's corpus location instead.
    val root: String,
    val games: List<RawGame>,
) {
    companion object {
        /** Bumped when the snapshot layout changes in a way older readers can't handle. */
        const val FORMAT_VERSION = 1
    }
}

/**
 * The JSON dialect a [LibrarySnapshot] is written and read with. `ignoreUnknownKeys` keeps a
 * snapshot forward-compatible with a newer writer; `encodeDefaults` writes the scan-metadata keys
 * (`scanner_version`, `reader_versions`) even at their defaults so the file stays complete.
 */
val LibrarySnapshotJson: Json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Builds a snapshot from a scan's raw output, storing every path relative to [root]. [scannerVersion]
 * and [readerVersions] are the producing logic's versions, so an import can reason about staleness.
 */
fun librarySnapshot(
    root: String,
    scannerVersion: Int,
    readerVersions: Map<String, Int>,
    games: List<RawGame>,
): LibrarySnapshot = LibrarySnapshot(
    scannerVersion = scannerVersion,
    readerVersions = readerVersions,
    root = root,
    games = games.map { it.relativized(root) },
)

/** Returns a copy of this game with every stored path made relative to [root], for snapshot storage. */
fun RawGame.relativized(root: String): RawGame = copy(
    photoUrl = photoUrl?.let { relative(it, root) },
    folderKey = relative(folderKey, root),
    tracks = tracks.map { it.relativized(root) },
)

/** Returns a copy of this game with its snapshot-relative paths rebased onto [root]. */
fun RawGame.rebased(root: String): RawGame = copy(
    photoUrl = photoUrl?.let { rebased(it, root) },
    folderKey = rebased(folderKey, root),
    tracks = tracks.map { it.rebased(root) },
)

/** Returns a copy of this track with its path and chain-file URIs made relative to [root]. */
fun RawTrack.relativized(root: String): RawTrack = copy(
    path = relative(path, root),
    chainFiles = chainFiles.map { it.copy(uri = relative(it.uri, root)) },
)

/** Returns a copy of this track with its path and chain-file URIs rebased onto [root]. */
fun RawTrack.rebased(root: String): RawTrack = copy(
    path = rebased(path, root),
    chainFiles = chainFiles.map { it.copy(uri = rebased(it.uri, root)) },
)

// Paths use `/` on every target (okio normalizes them) and the snapshot root is a `/`-separated
// reference, so the transforms are plain prefix arithmetic. A path that sits outside the root (which
// the scanner never produces — the corpus root is its only location) is left untouched rather than
// mangled.
private fun relative(path: String, root: String): String {
    val prefix = root.trimEnd('/')
    return when {
        prefix.isEmpty() -> path.trimStart('/')
        path == prefix -> ""
        path.startsWith("$prefix/") -> path.removePrefix("$prefix/")
        else -> path
    }
}

private fun rebased(path: String, root: String): String {
    if (isAbsolute(path)) return path
    val prefix = root.trimEnd('/')
    return if (path.isEmpty()) prefix else "$prefix/$path"
}

// A `/`-rooted POSIX path, a `C:/`-style Windows drive path, or a `//host` UNC path is already
// absolute — such a path was outside the corpus root when the snapshot was generated and is left as
// it is rather than re-rooted.
private fun isAbsolute(path: String): Boolean =
    path.startsWith('/') ||
        (path.length >= 2 && path[1] == ':') ||
        path.startsWith("\\\\")
