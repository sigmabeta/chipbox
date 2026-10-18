package net.sigmabeta.chipbox.server.scanner

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.sigmabeta.chipbox.scanner.DuplicateRsnContentException
import net.sigmabeta.chipbox.scanner.ExtractedRsnMember
import net.sigmabeta.chipbox.scanner.RsnExtractor
import net.sigmabeta.chipbox.utils.RSN_MEMBER_EXTENSION
import net.sigmabeta.chipbox.utils.rsnSpcMembers
import net.sigmabeta.sage.logging.Hatchet
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Path.Companion.toOkioPath

/**
 * Moves a freshly written member over its final path, replacing it.
 *
 * A seam so tests can force the "the target is locked" failure path. Production uses
 * [AtomicMemberMover], which asks the filesystem for an atomic replace first.
 */
internal fun interface MemberMover {
    /** Replace [target] with [source]; throws [IOException] when the move cannot be completed. */
    fun move(source: Path, target: Path)
}

/**
 * [MemberMover] that prefers an atomic replace and falls back to a plain move.
 *
 * On the JVM `FileSystem.atomicMove` maps to `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`.
 * Windows refuses that while another process holds the target without delete sharing, and some
 * filesystems do not support `ATOMIC_MOVE` at all; a non-atomic replace is then the only option.
 * If that fails too the exception propagates and the caller keeps the previous member.
 */
internal class AtomicMemberMover(
    private val fileSystem: FileSystem,
) : MemberMover {

    override fun move(source: Path, target: Path) {
        try {
            fileSystem.atomicMove(source, target)
        } catch (atomicFailure: IOException) {
            try {
                Files.move(
                    source.toNioPath(),
                    target.toNioPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (fallbackFailure: IOException) {
                fallbackFailure.addSuppressed(atomicFailure)
                throw fallbackFailure
            }
        }
    }
}

/**
 * Server-only [RsnExtractor]: writes each RSN's SPC members under
 * `<workDir>/rsn/<sha256(archive bytes)>/<member>.spc`.
 *
 * The output directory is content-addressed, so the same archive bytes map to the same paths
 * regardless of the archive's filename or library folder. Extraction is idempotent: a member is
 * only written when the file is missing or its bytes differ. Member order comes from
 * [rsnSpcMembers], which is the same order `RsnReader` gives tracks, so a track's `trackNumber`
 * indexes straight into the returned list.
 *
 * Writes are crash-safe without disturbing streaming: new bytes go to a `<member>.spc.tmp` file in
 * the same hash directory, are flushed and closed, and are then renamed over the final member
 * (atomically where the platform supports it). A reader serving the previous file keeps its open
 * handle and its bytes; a killed process can leave only a temporary file, never a short member
 * under the final name. Temporary names are never returned or persisted — every
 * [ExtractedRsnMember.path] is the final member path — and stale temporaries are removed on the
 * next extraction or prune pass.
 *
 * [prune] is deliberately conservative: it keeps a hash directory whenever any surviving track
 * path is under it, and tolerates delete failures (a member may be streaming). The scanner only
 * calls it after a fully successful scan, so a partial scan never deletes usable output.
 *
 * One archive content hash belongs to one archive, recorded by a `.source` marker file inside the
 * hash directory. A second archive with byte-identical content is refused with
 * [DuplicateRsnContentException] instead of writing the same member paths: extracted tracks are
 * identified by `(path, trackNumber)`, so admitting both would violate the unique index. The admin
 * is expected to remove the duplicate from the library. If the owning archive is renamed or
 * removed, the marker's path no longer exists and the survivor takes ownership on the next scan.
 *
 * Both the marker and the owner-existence check go through the injected [FileSystem], so a
 * non-system filesystem (a wrapper, an in-memory store) sees the same duplicate decisions
 * production does. The constructor is internal because [MemberMover] is a test seam, not API:
 * the server graph is this class's only production caller.
 */
class ServerRsnExtractor internal constructor(
    workDir: File,
    private val hatchet: Hatchet,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val mover: MemberMover = AtomicMemberMover(fileSystem),
) : RsnExtractor {

    // Absolute so persisted track paths stay absolute even when CHIPBOX_WORK_DIR is configured as
    // a relative path (the DB and the serving layer treat paths as absolute identifiers).
    private val extractionRoot: Path = workDir.absoluteFile.toOkioPath() / RSN_DIR

    // Scans run folders concurrently, and two byte-identical archives in different folders map to
    // the same content-addressed paths. Serialise extract/prune so a second writer can't truncate
    // a file a first writer just produced (and prune can't race an extraction).
    private val ioMutex = Mutex()

    override suspend fun extract(
        archiveBytes: ByteArray,
        source: String,
    ): List<ExtractedRsnMember>? {
        val members = rsnSpcMembers(archiveBytes)?.takeIf { it.isNotEmpty() } ?: return null

        val hash = archiveBytes.toByteString().sha256().hex()
        val directory = extractionRoot / hash
        val names = memberFileNames(members.map { it.name })

        return ioMutex.withLock {
            val owner = readOwner(directory)
            if (owner != null && owner != source && ownerStillExists(owner)) {
                throw DuplicateRsnContentException(source, owner)
            }
            fileSystem.createDirectories(directory)
            // First extraction, or the previous owner was renamed/removed: claim the hash.
            if (owner != source) writeOwner(directory, source)
            // Extraction is serialised, so any temporary file already here belongs to a killed
            // process and is safe to remove before this pass writes its own.
            deleteStaleTemps(directory)
            members.mapIndexed { index, member ->
                val target = directory / names[index]
                writeIfNeeded(directory, target, member.bytes)
                ExtractedRsnMember(name = member.name, path = target.toString())
            }
        }
    }

    /**
     * The archive that owns [directory], or null when the hash was never claimed (or a legacy
     * extraction predates the marker). A corrupt/unreadable marker is treated as absent; the next
     * extraction rewrites it.
     */
    private fun readOwner(directory: Path): String? = runCatching {
        val marker = directory / OWNER_MARKER
        if (fileSystem.metadataOrNull(marker)?.isRegularFile != true) return@runCatching null
        fileSystem.read(marker) { readUtf8() }.trim().ifEmpty { null }
    }.getOrNull()

    private fun writeOwner(directory: Path, source: String) {
        fileSystem.write(directory / OWNER_MARKER) { writeUtf8(source) }
    }

    /**
     * Whether the recorded owner archive is still on disk. Goes through the injected
     * [FileSystem] (never `java.io.File`) so a wrapped/non-system filesystem sees the same
     * decisions: an unreadable or invalid path counts as gone, exactly like [readOwner] treats a
     * bad marker, which lets the survivor take the hash over.
     */
    private fun ownerStillExists(owner: String): Boolean = runCatching {
        fileSystem.metadataOrNull(owner.toPath())?.isRegularFile == true
    }.getOrDefault(false)

    override suspend fun prune(keptPaths: Set<String>) {
        ioMutex.withLock {
            val normalizedKept = keptPaths.map { it.normalizedPath() }
            fileSystem.listOrNull(extractionRoot).orEmpty()
                .filter { fileSystem.metadataOrNull(it)?.isDirectory == true }
                .forEach { directory ->
                    val prefix = directory.toString().normalizedPath().trimEnd('/') + "/"
                    if (normalizedKept.any { it.startsWith(prefix) }) {
                        // A kept directory may still hold a temporary file from an extraction that
                        // was killed before its move; prune doubles as the cleanup pass.
                        deleteStaleTemps(directory)
                    } else {
                        deleteOrphanedDirectory(directory)
                    }
                }
        }
    }

    private fun deleteStaleTemps(directory: Path) {
        fileSystem.listOrNull(directory).orEmpty()
            .filter { it.name.endsWith(TEMP_SUFFIX) }
            .forEach { stale ->
                try {
                    fileSystem.delete(stale)
                    hatchet.d("Removed stale RSN extraction temp $stale.")
                } catch (e: IOException) {
                    // A temp is never served, so leaving it is harmless; the next pass retries.
                    hatchet.w("Could not remove stale RSN extraction temp $stale: ${e.message}")
                }
            }
    }

    private fun deleteOrphanedDirectory(directory: Path) {
        try {
            fileSystem.deleteRecursively(directory)
            hatchet.d("Pruned orphaned RSN extraction directory $directory.")
        } catch (e: IOException) {
            // A member may be open for playback (Windows) or the filesystem may be briefly busy.
            // Leaving the directory for the next successful scan is always safe.
            hatchet.w("Could not prune RSN extraction directory $directory: ${e.message}")
        }
    }

    /**
     * Writes [bytes] to a temporary file next to [target] and renames it over the final name.
     * Nothing is written in place, so a failure at any point leaves the previous member (and any
     * reader's handle) untouched; the move itself is what makes the new bytes visible.
     */
    private fun writeIfNeeded(directory: Path, target: Path, bytes: ByteArray) {
        if (matchesExisting(target, bytes)) return
        val temp = directory / (target.name + TEMP_SUFFIX)
        try {
            // write() closes the sink on return; flush explicitly so the complete member is on disk
            // before the rename makes it visible under the final name.
            fileSystem.write(temp) {
                write(bytes)
                flush()
            }
            mover.move(temp, target)
        } catch (e: IOException) {
            // Keep the previous member usable and leave the temp (complete or partial) for
            // deleteStaleTemps on the next extraction/prune pass, then fail this archive so the
            // scanner does not persist tracks pointing at output it could not produce.
            hatchet.w("Could not replace RSN member $target: ${e.message}")
            throw e
        }
    }

    private fun matchesExisting(target: Path, bytes: ByteArray): Boolean {
        val metadata = fileSystem.metadataOrNull(target) ?: return false
        val sameSize = metadata.isRegularFile && metadata.size == bytes.size.toLong()
        return sameSize &&
            runCatching { fileSystem.read(target) { readByteArray() }.contentEquals(bytes) }
                .getOrDefault(false)
    }

    companion object {
        const val RSN_DIR = "rsn"

        // Owner marker inside each hash directory. Starts with a dot so it is hidden on Unix and
        // cannot collide with an extracted member (those always end in `.spc`).
        internal const val OWNER_MARKER = ".source"

        // Suffix of an in-flight member write. Members always end in `.spc`, so `<member>.spc.tmp`
        // cannot collide with one, and it is never returned to the scanner or persisted.
        internal const val TEMP_SUFFIX = ".tmp"

        /**
         * Deterministic, path-traversal-free output names, one per member in member order.
         *
         * Directory components are stripped, unsafe characters replaced, and a name that occurs
         * more than once (case-insensitively — macOS/Windows filesystems are case-insensitive) is
         * prefixed with its zero-padded member index. If that prefix scheme ever collides with a
         * naturally-prefixed member name, every member gets the index prefix, which is unique by
         * construction.
         */
        internal fun memberFileNames(memberNames: List<String>): List<String> {
            val stems = memberNames.mapIndexed { index, name -> sanitizeStem(name, index) }
            val counts = stems.groupingBy { it.lowercase() }.eachCount()
            var names = stems.mapIndexed { index, stem ->
                if (counts.getValue(stem.lowercase()) > 1) indexedName(index, stem) else stem
            }
            if (names.groupingBy { it.lowercase() }.eachCount().values.any { it > 1 }) {
                names = stems.mapIndexed { index, stem -> indexedName(index, stem) }
            }
            return names.map { "$it.$RSN_MEMBER_EXTENSION" }
        }

        private fun indexedName(index: Int, stem: String): String =
            index.toString().padStart(2, '0') + "-" + stem

        private fun sanitizeStem(name: String, index: Int): String {
            // Both separators: member names inside an archive may come from a Unix or Windows host.
            val fileName = name.substringAfterLast('/').substringAfterLast('\\')
            val stem = fileName.substringBeforeLast('.', fileName)
            val cleaned = buildString {
                for (character in stem) {
                    when {
                        character.isLetterOrDigit() -> append(character)
                        character in SAFE_PUNCTUATION -> append(character)
                        character == '.' -> append('.')
                        else -> append('_')
                    }
                }
            }
                .replace(Regex("\\.{2,}"), "_")
                .trim()
                .trim('.', ' ')
            return cleaned.ifEmpty { "track-$index" }
        }

        private fun String.normalizedPath(): String = replace('\\', '/')

        private const val SAFE_PUNCTUATION = " -_()[]'&+,"
    }
}
