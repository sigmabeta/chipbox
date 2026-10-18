package net.sigmabeta.chipbox.scanner

/**
 * One SPC member written out of an RSN archive. [path] is the absolute filesystem path of the
 * extracted `.spc`, and the list index is the member's subsong index — the same order
 * `rsnSpcMembers` / [net.sigmabeta.chipbox.readers.RsnReader] use.
 */
data class ExtractedRsnMember(val name: String, val path: String)

/**
 * Optional scanner collaborator that turns RSN members into standalone `.spc` files.
 *
 * Only the server supplies one (its browser clients cannot decode RAR); desktop, Android, and CLI
 * leave it null and keep staging members out of the original archive at playback time. The
 * scanner calls [extract] after m3u overlays and filename-title fallbacks have been applied, then
 * rewrites each RSN track to the returned member path / `spc` extension / track number 0.
 *
 * Implementations must be deterministic: the same archive bytes must map to the same paths, and
 * [prune] must never delete output referenced by a surviving track.
 */
interface RsnExtractor {
    /**
     * Extract the SPC members of [archiveBytes] and return them in stable member order, or null
     * when the archive cannot be decoded or contains no SPC members. Members already present on
     * disk with matching bytes must be reused rather than rewritten.
     */
    suspend fun extract(archiveBytes: ByteArray): List<ExtractedRsnMember>?

    /**
     * Delete extraction output that no surviving track references. [keptPaths] is the set of
     * absolute track paths still in the library (including unchanged folders the scanner skipped).
     * Implementations must tolerate per-directory/file delete failures (e.g. a file being served).
     */
    suspend fun prune(keptPaths: Set<String>)
}
