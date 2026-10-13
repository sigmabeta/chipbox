package net.sigmabeta.chipbox.perfbench

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import net.sigmabeta.chipbox.contentsource.LocalFileContentSource
import net.sigmabeta.chipbox.database.fake.FakeDatabase
import net.sigmabeta.chipbox.player.emulators.vgmstream.VgmstreamProber
import net.sigmabeta.chipbox.player.emulators.vgmstream.VgmstreamSubsong
import net.sigmabeta.chipbox.readers.Readers
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.database.DatabaseRepository
import net.sigmabeta.chipbox.scanner.real.RealScanner
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.sage.logging.BluntHatchet
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/**
 * The portable-fixture round trip, end to end across both seams: scan a corpus once → serialize a
 * [LibrarySnapshot] → import that snapshot into a fresh library → the result is equivalent to
 * scanning the same corpus directly (and importing twice does not duplicate).
 *
 * The sample corpus is minimal pure-Kotlin PSF (incl. a `_lib` chain), so the scan needs no native
 * libraries; the injected prober is a no-op that keeps vgmstream out of the test.
 */
class SnapshotRoundTripTest {

    @Test
    fun `scan then import produces the same library as scanning directly`() {
        val corpus = sampleCorpus()
        val outFile = File(Files.createTempDirectory("perfbench-out").toFile(), "library-snapshot.json")

        val direct = scanDirectly(corpus)
        runSnapshot(SnapshotArgs(corpus, outFile), prober = NoVgmstreamProber)
        // Read the snapshot back off disk — the exact path the app's ingest takes — so a schema
        // change that breaks the written format fails here rather than at runtime.
        val snapshot = PerfbenchJson.decodeFromString<LibrarySnapshot>(outFile.readText())

        val imported = newRepo()
        runBlocking { imported.importLibrarySnapshot(snapshot, corpus.invariantSeparatorsPath) }

        assertEquals(libraryShape(direct), libraryShape(imported))
    }

    @Test
    fun `importing the generated snapshot twice is idempotent`() {
        val corpus = sampleCorpus()
        val outFile = File(Files.createTempDirectory("perfbench-out").toFile(), "library-snapshot.json")
        runSnapshot(SnapshotArgs(corpus, outFile), prober = NoVgmstreamProber)
        val snapshot = PerfbenchJson.decodeFromString<LibrarySnapshot>(outFile.readText())

        val repo = newRepo()
        val first = runBlocking { repo.importLibrarySnapshot(snapshot, corpus.invariantSeparatorsPath) }
        val second = runBlocking { repo.importLibrarySnapshot(snapshot, corpus.invariantSeparatorsPath) }

        assertEquals(1, first.added)
        assertEquals(LibraryImportResult(added = 0, updated = 0, unchanged = 1), second)
    }

    // ---- helpers ----

    private fun sampleCorpus(): File {
        val corpus = Files.createTempDirectory("perfbench-corpus").toFile()
        val gameDir = File(corpus, "PilotWings").apply { mkdirs() }
        File(gameDir, "lib.psflib").writeBytes(psfFile(tagText = ""))
        File(gameDir, "01 Takeoff.psf").writeBytes(
            psfFile(tagText = "_lib=lib.psflib\ntitle=Takeoff\ngame=PilotWings\nartist=Koji Kondo\n"),
        )
        return corpus
    }

    private fun scanDirectly(corpus: File): DatabaseRepository {
        val repo = newRepo()
        val locationsFile = File.createTempFile("perfbench-locations", ".txt")
        val source = LocalFileContentSource(FileSystem.SYSTEM, locationsFile.toOkioPath())
        source.addLocation(corpus.toOkioPath())
        val scanner = RealScanner(repo, source, Readers(BluntHatchet()), NoVgmstreamProber, BluntHatchet())
        runBlocking {
            scanner.startScan()
            scanner.state().first { it is ScannerState.Complete || it is ScannerState.Failed }
        }
        locationsFile.delete()
        return repo
    }

    private fun newRepo(): DatabaseRepository {
        val db = FakeDatabase()
        return DatabaseRepository(
            artistDao = db.artistDao,
            gameDao = db.gameDao,
            trackDao = db.trackDao,
            gameArtistDao = db.gameArtistDao,
            trackArtistDao = db.trackArtistDao,
            searchHistoryDao = db.searchHistoryDao,
            hatchet = BluntHatchet(),
        )
    }

    // A repository comparison shape that ignores ids and timestamps — the parts a snapshot does not
    // carry — so the assertion is about content equivalence, not incidental bookkeeping.
    private data class GameShape(
        val title: String,
        val copyright: String?,
        val releaseDate: String?,
        val genre: String?,
        val titleJp: String?,
        val tracks: List<TrackShape>,
    )

    private data class TrackShape(
        val title: String,
        val path: String,
        val trackNumber: Int,
        val chainFiles: List<String>,
        val artists: List<String>,
    )

    private fun libraryShape(repo: Repository): List<GameShape> = runBlocking {
        val data = repo.getAllGames(withTracks = true, withArtists = true).first { it !is Data.Loading }
        check(data is Data.Succeeded) { "expected a populated library; got $data" }
        data.data
            .map { game ->
                GameShape(
                    title = game.title,
                    copyright = game.copyright,
                    releaseDate = game.releaseDate,
                    genre = game.genre,
                    titleJp = game.titleJp,
                    tracks = game.tracks.orEmpty().map { track ->
                        TrackShape(
                            title = track.title,
                            path = track.path,
                            trackNumber = track.trackNumber,
                            chainFiles = track.chainFiles.map { it.uri },
                            artists = track.artists.orEmpty().map { it.name }.sorted(),
                        )
                    },
                )
            }
            .sortedBy { it.title }
    }

    private fun psfFile(tagText: String): ByteArray {
        val tagBytes = "[TAG]".encodeToByteArray() + tagText.encodeToByteArray()
        val out = ByteArray(16 + tagBytes.size)
        "PSF".encodeToByteArray().copyInto(out, 0)
        out[3] = 0x01 // PSX
        tagBytes.copyInto(out, 16)
        return out
    }

    /** No-op prober: the sample corpus is pure-Kotlin PSF, so vgmstream must never need to load. */
    private object NoVgmstreamProber : VgmstreamProber {
        override fun isSupported(extension: String): Boolean = false

        override fun probe(bytes: ByteArray, extension: String): List<VgmstreamSubsong> = emptyList()
    }
}
