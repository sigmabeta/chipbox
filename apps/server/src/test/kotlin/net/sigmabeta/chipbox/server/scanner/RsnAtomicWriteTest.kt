package net.sigmabeta.chipbox.server.scanner

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import net.sigmabeta.chipbox.server.testing.RarEntry
import net.sigmabeta.chipbox.server.testing.rar4
import net.sigmabeta.chipbox.server.testing.spcFile
import net.sigmabeta.chipbox.server.testing.tempDir
import net.sigmabeta.sage.logging.BluntHatchet
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path

/**
 * Member writes must be atomic from a concurrent reader's point of view: a rewrite lands in a
 * temporary file in the same directory and is moved over the final name, so a client streaming
 * the old file keeps its handle and its bytes. The final member name is the only name the
 * scanner or the JSON API ever sees.
 *
 * These tests drive [ServerRsnExtractor] directly (the `RsnExtractor` seam) rather than a full
 * scan, because the behaviours under test are per-member writes, not indexing.
 */
class RsnAtomicWriteTest {

    @Test
    fun `a repair replaces the file instead of truncating a reader's handle`() = runBlocking {
        val workDir = tempDir("rsn-atomic-work")
        val first = spcFile(title = "First")
        val archive = rar4(RarEntry("01 First.spc", first))
        val extractor = ServerRsnExtractor(workDir, BluntHatchet())
        val target = File(extractor.extract(archive, SOURCE)!!.single().path)

        // A killed process left a short member behind, and a client is streaming that file.
        target.writeBytes(first.copyOf(3))
        RandomAccessFile(target, "r").use { reader ->
            val repaired = File(extractor.extract(archive, SOURCE)!!.single().path)
            assertEquals(target, repaired)
            assertTrue(
                repaired.readBytes().contentEquals(first),
                "the final name must hold the complete member",
            )
            assertEquals(3L, reader.length(), "the handle keeps the bytes it was served")
        }
    }

    @Test
    fun `a partial member is repaired and no temporary name is exposed`() = runBlocking {
        val workDir = tempDir("rsn-atomic-work")
        val first = spcFile(title = "First")
        val archive = rar4(RarEntry("01 First.spc", first))
        val extractor = ServerRsnExtractor(workDir, BluntHatchet())

        val member = extractor.extract(archive, SOURCE)!!.single()
        val target = File(member.path)
        assertFalse(member.path.endsWith(ServerRsnExtractor.TEMP_SUFFIX), "temp leaked: $member")

        target.writeBytes(byteArrayOf(1, 2, 3))
        val repaired = extractor.extract(archive, SOURCE)!!.single()

        assertEquals(target.absolutePath, repaired.path)
        assertTrue(target.readBytes().contentEquals(first))
        assertEquals(emptyList(), tempFilesIn(target.parentFile).map { it.name })
    }

    @Test
    fun `a matching member is not rewritten`() = runBlocking {
        val workDir = tempDir("rsn-atomic-work")
        val archive = rar4(RarEntry("01 First.spc", spcFile(title = "First")))
        val extractor = ServerRsnExtractor(workDir, BluntHatchet())
        val target = File(extractor.extract(archive, SOURCE)!!.single().path)

        // A distinctive timestamp proves nothing replaced the file on the second pass.
        val untouched = FileTime.fromMillis(1_000_000_000_000)
        Files.setLastModifiedTime(target.toPath(), untouched)

        extractor.extract(archive, SOURCE)

        assertEquals(untouched.toMillis(), Files.getLastModifiedTime(target.toPath()).toMillis())
        assertEquals(emptyList(), tempFilesIn(target.parentFile).map { it.name })
    }

    @Test
    fun `a failed move keeps the previous member and the next extraction retries`() = runBlocking {
        val workDir = tempDir("rsn-atomic-work")
        val first = spcFile(title = "First")
        val archive = rar4(RarEntry("01 First.spc", first))
        val mover = FlakyMover()
        val extractor = ServerRsnExtractor(workDir, BluntHatchet(), mover = mover)
        val target = File(extractor.extract(archive, SOURCE)!!.single().path)
        val partial = first.copyOf(3)
        target.writeBytes(partial)

        // The target is locked (Windows, a streaming reader): the move fails, the old member is
        // still what the final name serves, and the new bytes stay behind as a temp file.
        mover.failNextMove = true
        assertFailsWith<IOException> { extractor.extract(archive, SOURCE) }
        assertTrue(target.readBytes().contentEquals(partial), "the previous member stays usable")
        assertEquals(listOf("01 First.spc.tmp"), tempFilesIn(target.parentFile).map { it.name })

        // The next pass retries the write, replaces the member, and sweeps the temp file.
        extractor.extract(archive, SOURCE)
        assertTrue(target.readBytes().contentEquals(first))
        assertEquals(emptyList(), tempFilesIn(target.parentFile).map { it.name })
    }

    @Test
    fun `a filesystem without atomic move falls back to a plain replace`() = runBlocking {
        val workDir = tempDir("rsn-atomic-work")
        val first = spcFile(title = "First")
        val archive = rar4(RarEntry("01 First.spc", first))
        val extractor = ServerRsnExtractor(
            workDir,
            BluntHatchet(),
            fileSystem = NoAtomicMoveFileSystem(FileSystem.SYSTEM),
        )
        val target = File(extractor.extract(archive, SOURCE)!!.single().path)
        target.writeBytes(first.copyOf(3))

        val repaired = extractor.extract(archive, SOURCE)!!.single()

        assertEquals(target.absolutePath, repaired.path)
        assertTrue(target.readBytes().contentEquals(first))
        assertEquals(emptyList(), tempFilesIn(target.parentFile).map { it.name })
    }

    @Test
    fun `the next extraction removes a stale temp file`() = runBlocking {
        val workDir = tempDir("rsn-atomic-work")
        val archive = rar4(RarEntry("01 First.spc", spcFile(title = "First")))
        val extractor = ServerRsnExtractor(workDir, BluntHatchet())
        val target = File(extractor.extract(archive, SOURCE)!!.single().path)

        // Left by a process killed between writing the temp and moving it into place.
        val stale = File(target.parentFile, target.name + ServerRsnExtractor.TEMP_SUFFIX)
        stale.writeBytes(byteArrayOf(9, 9))

        extractor.extract(archive, SOURCE)

        assertFalse(stale.exists(), "a stale temp must not survive the next extraction")
        assertTrue(target.isFile, "the member itself is kept")
    }

    @Test
    fun `prune removes a stale temp file from a kept directory`() = runBlocking {
        val workDir = tempDir("rsn-atomic-work")
        val archive = rar4(RarEntry("01 First.spc", spcFile(title = "First")))
        val extractor = ServerRsnExtractor(workDir, BluntHatchet())
        val member = extractor.extract(archive, SOURCE)!!.single()
        val stale = File(File(member.path).parentFile, "01 First.spc.tmp")
        stale.writeBytes(byteArrayOf(9, 9))

        // The directory is referenced by a surviving track, so prune must keep it — but it can
        // still sweep temporaries, which nothing ever serves.
        extractor.prune(setOf(member.path))

        assertFalse(stale.exists(), "a stale temp must not survive a prune pass")
        assertTrue(File(member.path).isFile)
    }

    private fun tempFilesIn(directory: File): List<File> =
        directory.listFiles { file -> file.name.endsWith(ServerRsnExtractor.TEMP_SUFFIX) }
            ?.sortedBy { it.name }
            .orEmpty()

    /** Delegates to [delegate] but refuses atomic moves, like a filesystem without `ATOMIC_MOVE`. */
    private class NoAtomicMoveFileSystem(delegate: FileSystem) : ForwardingFileSystem(delegate) {
        override fun atomicMove(source: Path, target: Path): Unit =
            throw IOException("atomic move not supported")
    }

    /** A [MemberMover] that can fail the next move, standing in for a locked Windows target. */
    private class FlakyMover : MemberMover {
        var failNextMove = false

        override fun move(source: Path, target: Path) {
            if (failNextMove) {
                failNextMove = false
                throw IOException("target is locked")
            }
            FileSystem.SYSTEM.atomicMove(source, target)
        }
    }

    private companion object {
        const val SOURCE = "corpus/tiny.rsn"
    }
}
