package net.sigmabeta.chipbox.scanner

/**
 * One SPC member written out of an RSN archive. [path] is the absolute filesystem path of the
 * extracted `.spc`, and the list index is the member's subsong index — the same order
 * `rsnSpcMembers` / [net.sigmabeta.chipbox.readers.RsnReader] use.
 */
data class ExtractedRsnMember(val name: String, val path: String)

/**
 * Thrown by an [RsnExtractor] when the archive at [source] has exactly the same bytes as another
 * archive ([owner]) that already owns those bytes.
 *
 * Content-addressed extraction gives one set of member paths to one archive content, so a second
 * archive with identical bytes would collide with the owner's persisted tracks. The extractor
 * refuses it instead; the scanner drops that archive's tracks and reports a per-file failure. The
 * admin removes the duplicate and restarts to get a clean scan.
 *
 * Ownership is not limited to the current scan: an extractor may persist it. The server records
 * [source] in a marker beside the extracted members, so a byte-identical copy is still refused
 * when the owning folder was skipped as unchanged on a later scan.
 */
class DuplicateRsnContentException(
    val source: String,
    val owner: String,
) : Exception("RSN archive '$source' has the same content as '$owner'")

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
     *
     * [source] is the archive's stable identity across scans (library folder + filename). It is
     * used for duplicate detection and error messages, and implementations **may persist it**:
     * the server writes it into a marker beside the content-addressed members so a later scan can
     * still tell which archive owns a hash — even when that archive's folder was skipped as
     * unchanged — and refuse a byte-identical copy. Callers must therefore pass the same value for
     * the same logical archive on every scan.
     *
     * @throws DuplicateRsnContentException when another archive with the same bytes already owns
     *   this content (this scan or a previous one, per the persisted marker). The caller reports
     *   the duplicate and drops its tracks.
     */
    suspend fun extract(archiveBytes: ByteArray, source: String): List<ExtractedRsnMember>?

    /**
     * Delete extraction output that no surviving track references. [keptPaths] is the set of
     * absolute track paths still in the library (including unchanged folders the scanner skipped).
     * Implementations must tolerate per-directory/file delete failures (e.g. a file being served).
     */
    suspend fun prune(keptPaths: Set<String>)
}
