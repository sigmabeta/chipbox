package net.sigmabeta.chipbox.util

import net.sigmabeta.chipbox.backend.Scanner
import net.sigmabeta.chipbox.backend.Scanner.Companion.EXTENSIONS_MUSIC
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.model.repository.RealmRepository
import timber.log.Timber
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

val GME_PLATFORM_SNES = "Super Nintendo"
val GME_PLATFORM_GENESIS = "Sega SMS/Genesis"

val TYPE_OTHER = -1
val TYPE_FOLDER = 0
val TYPE_TRACK = 1

val TRACK_LENGTH_DEFAULT = 150000L

val EXTENSIONS_MULTI_TRACK: HashSet<String> = HashSet(arrayListOf(
        "nsf", "nsfe", "gbs")
)

val EXTENSIONS_IMAGES: HashSet<String> = HashSet(arrayListOf(
        "jpg", "png")
)

fun readSingleTrackFile(file: File, trackNumber: Int): Track? {
    val scanner = EXTENSIONS_MUSIC[file.extension] ?: return null

    val path = file.path
    val error = scanner.fileInfoSetup(path)

    if (error != null) {
        Timber.e("Error reading file: %s", error)
        return null
    }

    val track = getTrack(scanner, path, 0)

    track?.trackNumber = trackNumber

    scanner.fileInfoTeardown()

    return track
}

fun readMultipleTrackFile(file: File): List<Track>? {
    val scanner = EXTENSIONS_MUSIC[file.extension] ?: return null

    val path = file.path
    val error = scanner.fileInfoSetup(path)

    if (error != null) {
        Timber.e("Error reading file: %s", error)
        return null
    }

    val trackCount = scanner.fileInfoGetTrackCount()

    val tracks = ArrayList<Track>(trackCount)
    for (trackNumber in 0..trackCount - 1) {
        val track = getTrack(scanner, path, trackNumber)
        if (track != null) {
            if (track.title.isNullOrEmpty()) {
                track.title = "${track.gameTitle} Track ${trackNumber + 1}"
            }

            track.trackNumber = trackNumber + 1

            tracks.add(track)
        }
    }

    scanner.fileInfoTeardown()
    return tracks
}

private fun getTrack(scanner: Scanner, path: String, trackNumber: Int): Track? {
    scanner.fileInfoSetTrackNumber(trackNumber)

    val platform = scanner.getFilePlatform()?.convert() ?: return null

    var artist = scanner.getFileArtist()?.convert() ?: RealmRepository.ARTIST_UNKNOWN
    if (artist.isBlank()) {
        artist = RealmRepository.ARTIST_UNKNOWN
    }

    var trackLength = 0L

    val fileTrackLength = scanner.getFileTrackLength()
    val fileIntroLength = scanner.getFileIntroLength()
    val fileLoopLength = scanner.getFileLoopLength()

    if (fileTrackLength > 0) {
        trackLength = fileTrackLength
    }

    if (fileLoopLength > 0) {
        trackLength = fileIntroLength + (fileLoopLength * 2)
    }

    if (trackLength == 0L) {
        trackLength = TRACK_LENGTH_DEFAULT
    }

    val track = Track(trackNumber,
            path,
            scanner.getFileTitle()?.convert() ?: RealmRepository.TITLE_UNKNOWN,
            scanner.getFileGameTitle()?.convert() ?: RealmRepository.GAME_UNKNOWN,
            artist,
            platform,
            trackLength,
            fileIntroLength,
            fileLoopLength,
            scanner.getBackendId()
    )

    return track
}

fun getTimeStringFromMillis(millis: Long): String {
    val millisAsLong = millis.toLong()

    val minutes = TimeUnit.MILLISECONDS.toMinutes(millisAsLong)
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millisAsLong)
    val displaySeconds = totalSeconds - TimeUnit.MINUTES.toSeconds(minutes)

    return "%d:%02d".format(minutes, displaySeconds)
}

