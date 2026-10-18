package net.sigmabeta.chipbox.server.scanner

import java.io.File
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
import okio.Path.Companion.toOkioPath

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
 */
class ServerRsnExtractor(
    workDir: File,
    private val hatchet: Hatchet,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
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
            if (owner != null && owner != source && File(owner).isFile) {
                throw DuplicateRsnContentException(source, owner)
            }
            fileSystem.createDirectories(directory)
            // First extraction, or the previous owner was renamed/removed: claim the hash.
            if (owner != source) writeOwner(directory, source)
            members.mapIndexed { index, member ->
                val target = directory / names[index]
                writeIfNeeded(target, member.bytes)
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

    override suspend fun prune(keptPaths: Set<String>) {
        ioMutex.withLock {
            val normalizedKept = keptPaths.map { it.normalizedPath() }
            fileSystem.listOrNull(extractionRoot).orEmpty()
                .filter { fileSystem.metadataOrNull(it)?.isDirectory == true }
                .filterNot { directory ->
                    val prefix = directory.toString().normalizedPath().trimEnd('/') + "/"
                    normalizedKept.any { it.startsWith(prefix) }
                }
                .forEach(::deleteOrphanedDirectory)
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

    private fun writeIfNeeded(target: Path, bytes: ByteArray) {
        if (matchesExisting(target, bytes)) return
        // Write in place: the content hash keys the directory, so a torn write can only be a
        // wrong-sized/partial file that the next scan detects and rewrites. Keeping the filename
        // stable (no temp rename) also means a concurrently-streaming file is never unlinked.
        fileSystem.write(target) { write(bytes) }
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
