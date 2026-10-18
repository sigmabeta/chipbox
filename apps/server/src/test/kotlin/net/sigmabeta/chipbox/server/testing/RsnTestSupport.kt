package net.sigmabeta.chipbox.server.testing

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.CRC32
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.sigmabeta.chipbox.contentsource.LocalFileContentSource
import net.sigmabeta.chipbox.database.ChipboxDatabase
import net.sigmabeta.chipbox.database.dao.ArtistDao
import net.sigmabeta.chipbox.database.dao.GameArtistDao
import net.sigmabeta.chipbox.database.dao.GameDao
import net.sigmabeta.chipbox.database.dao.SearchHistoryDao
import net.sigmabeta.chipbox.database.dao.TrackArtistDao
import net.sigmabeta.chipbox.database.dao.TrackDao
import net.sigmabeta.chipbox.database.fake.FakeDatabase
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.emulators.vgmstream.VgmstreamProber
import net.sigmabeta.chipbox.player.emulators.vgmstream.VgmstreamSubsong
import net.sigmabeta.chipbox.readers.Readers
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.database.DatabaseRepository
import net.sigmabeta.chipbox.scanner.RsnExtractor
import net.sigmabeta.chipbox.scanner.real.RealScanner
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.sage.logging.BluntHatchet
import net.sigmabeta.sage.logging.Hatchet
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/**
 * Fixtures and harness pieces shared by the server's RSN tests.
 *
 * The RAR4 archive is generated here rather than committed: junrar is read-only and no external
 * `rar`/`7z` tool is available (or assumed in CI). Each member is a stored (method 0x30) entry, so
 * the archive is a plain container around the bytes — deterministic and tiny relative to a real
 * RSN. Header CRCs are not validated by junrar for RAR4 headers, but the member CRC32 is, so the
 * bytes are checked to be a genuinely readable archive whenever a test scans them.
 */
internal data class RarEntry(val name: String, val bytes: ByteArray)

internal fun rar4(vararg entries: RarEntry): ByteArray {
    val out = ByteArrayOutputStream()

    // Signature block: the RAR4 marker is itself a 7-byte block header.
    out.write(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00))

    // Main archive header: CRC (unchecked by junrar), type 0x73, flags 0, size 13, 6 reserved bytes.
    out.writeLe16(0)
    out.write(0x73)
    out.writeLe16(0)
    out.writeLe16(13)
    out.writeLe16(0)
    out.writeLe32(0)

    for (entry in entries) {
        val name = entry.name.encodeToByteArray()
        val data = entry.bytes
        val crc = CRC32().apply { update(data) }

        // File header: base block + packed size + unpacked size + host OS + CRC + DOS time +
        // unpack version + method + name length + attributes + name (32 + name bytes total).
        out.writeLe16(0)
        out.write(0x74)
        out.writeLe16(0x8000) // LONG_BLOCK
        out.writeLe16(32 + name.size)
        out.writeLe32(data.size) // packed size
        out.writeLe32(data.size) // unpacked size
        out.write(0) // MS-DOS host
        out.writeLe32(crc.value.toInt())
        out.writeLe32(0) // DOS timestamp
        out.write(20) // unpack version 2.0
        out.write(0x30) // stored, no compression
        out.writeLe16(name.size)
        out.writeLe32(0x20) // archive attribute
        out.write(name)
        out.write(data)
    }

    // End-of-archive header: junrar validates the fixed CRC/type/flags/size of this block.
    out.writeLe16(0x3DC4)
    out.write(0x7B)
    out.writeLe16(0x4000)
    out.writeLe16(7)
    return out.toByteArray()
}

/**
 * A minimal but valid SPC file: the 33-byte header magic, the 0x1A ID666 marker, and the
 * title/game/artist/length fields [net.sigmabeta.chipbox.readers.SpcReader] reads. The body is
 * zero-filled, which is fine — scanning only reads tags, never audio.
 */
internal fun spcFile(
    title: String,
    game: String = "Fixture Game",
    artist: String = "Fixture Artist",
    lengthSeconds: Int = 10,
): ByteArray {
    val bytes = ByteArray(SPC_SIZE)
    "SNES-SPC700 Sound File Data v0.30".encodeToByteArray().copyInto(bytes, 0)
    bytes[35] = 0x1A

    fun put(value: String, offset: Int, fieldSize: Int) {
        val encoded = value.encodeToByteArray()
        encoded.copyInto(bytes, offset, 0, minOf(encoded.size, fieldSize))
    }
    put(title, TITLE_OFFSET, TAG_LENGTH)
    put(game, GAME_OFFSET, TAG_LENGTH)
    put(artist, ARTIST_OFFSET, TAG_LENGTH)
    put(lengthSeconds.toString(), LENGTH_OFFSET, LENGTH_FIELD_SIZE)
    put("0", FADE_OFFSET, FADE_FIELD_SIZE)
    return bytes
}

internal data class ScanResult(val repository: DatabaseRepository, val state: ScannerState)

/**
 * Scans [corpus] into a fresh [DatabaseRepository] over [FakeDatabase] with a no-op vgmstream
 * prober (the fixture is pure Kotlin, and no native vgmstream library exists on this test host).
 */
internal fun scanCorpus(
    corpus: File,
    workDir: File,
    extractor: RsnExtractor? = null,
    repository: DatabaseRepository = newRepository(),
    hatchet: Hatchet = BluntHatchet(),
): ScanResult {
    val source = LocalFileContentSource(FileSystem.SYSTEM, File(workDir, "locations.txt").toOkioPath())
    source.addLocation(corpus.toOkioPath())
    val scanner = RealScanner(
        repository,
        source,
        Readers(hatchet),
        NoVgmstreamProber,
        hatchet,
        rsnExtractor = extractor,
    )
    val state = runBlocking {
        scanner.startScan()
        scanner.state().first { it is ScannerState.Complete || it is ScannerState.Failed }
    }
    return ScanResult(repository, state)
}

internal fun tempDir(prefix: String): File =
    Files.createTempDirectory(prefix).toFile().also { it.deleteOnExit() }

internal fun ByteArray.sha256Hex(): String =
    MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

internal fun newRepository(): DatabaseRepository {
    val database = FakeDatabase()
    return newRepository(
        artistDao = database.artistDao,
        gameDao = database.gameDao,
        trackDao = database.trackDao,
        gameArtistDao = database.gameArtistDao,
        trackArtistDao = database.trackArtistDao,
        searchHistoryDao = database.searchHistoryDao,
    )
}

/** A repository over a real (Room/SQLite) database — the only backing that enforces indexes. */
internal fun newRepository(database: ChipboxDatabase): DatabaseRepository = newRepository(
    artistDao = database.artistDao(),
    gameDao = database.gameDao(),
    trackDao = database.trackDao(),
    gameArtistDao = database.gameArtistDao(),
    trackArtistDao = database.trackArtistDao(),
    searchHistoryDao = database.searchHistoryDao(),
)

internal fun newRepository(
    artistDao: ArtistDao,
    gameDao: GameDao,
    trackDao: TrackDao,
    gameArtistDao: GameArtistDao,
    trackArtistDao: TrackArtistDao,
    searchHistoryDao: SearchHistoryDao,
): DatabaseRepository = DatabaseRepository(
    artistDao = artistDao,
    gameDao = gameDao,
    trackDao = trackDao,
    gameArtistDao = gameArtistDao,
    trackArtistDao = trackArtistDao,
    searchHistoryDao = searchHistoryDao,
    hatchet = BluntHatchet(),
)

internal fun Repository.allTracks(): List<Track> = runBlocking {
    when (val data = getAllTracks(withGame = true).first { it !is Data.Loading }) {
        is Data.Succeeded -> data.data
        is Data.Empty -> emptyList()
        is Data.Failed -> error("repository failed: ${data.message}")
        Data.Loading -> error("unreachable")
    }
}

internal fun Repository.allGames(): List<Game> = runBlocking {
    when (val data = getAllGames(withTracks = true).first { it !is Data.Loading }) {
        is Data.Succeeded -> data.data
        is Data.Empty -> emptyList()
        is Data.Failed -> error("repository failed: ${data.message}")
        Data.Loading -> error("unreachable")
    }
}

/** No-op prober: these fixtures never need the native vgmstream library. */
internal object NoVgmstreamProber : VgmstreamProber {
    override fun isSupported(extension: String): Boolean = false

    override fun probe(bytes: ByteArray, extension: String): List<VgmstreamSubsong> = emptyList()
}

/**
 * Records every message (and the error subset) so a test can assert what the scanner told the
 * admin — e.g. that a duplicate-archive refusal names both archives.
 */
internal class RecordingHatchet : Hatchet {
    val messages: MutableList<String> = mutableListOf()
    val errors: MutableList<String> = mutableListOf()

    override fun v(message: String) {
        messages += message
    }

    override fun d(message: String) {
        messages += message
    }

    override fun i(message: String) {
        messages += message
    }

    override fun w(message: String) {
        messages += message
    }

    override fun e(message: String) {
        messages += message
        errors += message
    }

    override fun log(severity: Int, message: String) {
        messages += message
        if (severity >= ERROR_SEVERITY) errors += message
    }

    private companion object {
        // Same threshold BluntHatchet/BasicHatchet use for `recentErrors`.
        const val ERROR_SEVERITY = 6
    }
}

private fun ByteArrayOutputStream.writeLe16(value: Int) {
    write(value and 0xFF)
    write((value ushr 8) and 0xFF)
}

private fun ByteArrayOutputStream.writeLe32(value: Int) {
    write(value and 0xFF)
    write((value ushr 8) and 0xFF)
    write((value ushr 16) and 0xFF)
    write((value ushr 24) and 0xFF)
}

private const val SPC_SIZE = 0x10200
private const val TAG_LENGTH = 32

// Header layout: 33-byte magic, 3-byte header-info (last byte 0x1A), 1-byte minor version,
// 9 register bytes, then the ID666 text fields.
private const val TITLE_OFFSET = 46
private const val GAME_OFFSET = 78
private const val LENGTH_OFFSET = 169
private const val LENGTH_FIELD_SIZE = 3
private const val FADE_OFFSET = 172
private const val FADE_FIELD_SIZE = 5
private const val ARTIST_OFFSET = 177
