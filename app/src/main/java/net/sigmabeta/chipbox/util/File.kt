package net.sigmabeta.chipbox.util

import net.sigmabeta.chipbox.model.file.FileListItem
import net.sigmabeta.chipbox.model.objects.Track
import net.sigmabeta.chipbox.util.external.*
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

val GME_PLATFORM_SNES = "Super Nintendo"
val GME_PLATFORM_GENESIS = "Sega SMS/Genesis"

val TYPE_OTHER = -1
val TYPE_FOLDER = 0

val EXTENSIONS_MUSIC: HashSet<String> = HashSet(arrayListOf(
        ".spc", ".vgm", ".vgz", ".nsf", ".gbs")
)

val EXTENSIONS_MULTI_TRACK: HashSet<String> = HashSet(arrayListOf(
        ".nsf", ".gbs")
)

val EXTENSIONS_IMAGES: HashSet<String> = HashSet(arrayListOf(
        ".jpg", ".png")
)

fun getFileType(file: File): Int {
    if (file.isDirectory) {
        return TYPE_FOLDER
    } else {
        val path = file.absolutePath

        val extensionStart = path.lastIndexOf('.')
        if (extensionStart < 1) {
            // Ignore hidden files & files without extensions.
            return TYPE_OTHER
        } else {
            val fileExtension = path.substring(extensionStart)

            // Check that the file has an extension we care about before trying to read out of it.
            if (EXTENSIONS_MUSIC.contains(fileExtension)) {
                // Ask the native library what platform this song is from.
                return getPlatform(path)
            } else {
                return TYPE_OTHER
            }
        }
    }
}

fun getFileExtension(filePath: String): String? {
    val extensionStart = filePath.lastIndexOf('.')

    if (extensionStart > 0) {
        return filePath.substring(extensionStart)
    } else {
        return null
    }
}

fun readSingleTrackFile(path: String, trackNumber: Int): Track? {
    val error = fileInfoSetupNative(path)

    if (error != null) {
        logError("[File] Error reading file: $error")
        return null
    }

    val track = getTrack(path, 0)

    track?.trackNumber = trackNumber

    fileInfoTeardownNative()

    return track
}

fun readMultipleTrackFile(path: String): List<Track>? {
    val error = fileInfoSetupNative(path)

    if (error != null) {
        logError("[File] Error reading file: $error")
        return null
    }

    val trackCount = fileInfoGetTrackCount()

    val tracks = ArrayList<Track>(trackCount)
    for (trackNumber in 0..trackCount - 1) {
        val track = getTrack(path, trackNumber)
        if (track != null) {
            if (track.title.isEmpty()) {
                track.title = "${track.gameTitle} Track ${trackNumber + 1}"
            }

            track.trackNumber = trackNumber + 1

            tracks.add(track)
        }
    }

    fileInfoTeardownNative()
    return tracks
}

private fun getTrack(path: String, trackNumber: Int): Track? {
    fileInfoSetTrackNumberNative(trackNumber)

    val platform = getTrackPlatform() ?: return null

    val track = Track(
            -1,
            trackNumber,
            path,
            getFileTitle().convert(),
            -1,
            getFileGameTitle().convert(),
            platform,
            getFileArtist().convert(),
            getFileTrackLength(),
            getFileIntroLength(),
            getFileLoopLength()
    )

    return track
}

private fun getTrackPlatform(): Int? {
    val platformString = getFilePlatform().convert()

    val platform = when (platformString) {
        "Super Nintendo" -> Track.PLATFORM_SNES
        "Sega Mega Drive", "Sega Mega Drive / Genesis", "Sega MegaDrive / Genesis", "Sega Genesis" -> Track.PLATFORM_GENESIS
        "Sega 32X / Mega 32X", "Sega 32X" -> Track.PLATFORM_32X
        "Nintendo Entertainment System", "Famicom", "Nintendo NES" -> Track.PLATFORM_NES
        "Game Boy" -> Track.PLATFORM_GAMEBOY
        else -> {
            logError("[File] Unsupported platform: $platformString")
            null
        }
    }
    return platform
}

fun getPlatform(path: String): Int {
    when (getPlatformNative(path)) {
        GME_PLATFORM_GENESIS -> return Track.PLATFORM_GENESIS
        GME_PLATFORM_SNES -> return Track.PLATFORM_SNES
        else -> return TYPE_OTHER

    // TODO Handle error state (i.e non-vgm file with vgm extension)
    // TODO Handle other systems.
    }
}

fun createFileListItem(file: File): FileListItem {
    val type = getFileType(file)

    return FileListItem(type, file.name, file.absolutePath)
}

fun generateFileList(folder: File): ArrayList<FileListItem> {
    val children = folder.listFiles() ?: return ArrayList<FileListItem>()

    val fileList = ArrayList<FileListItem>(children.size)

    for (child in children) {
        if (!child.isHidden) {
            val item = createFileListItem(child)
            fileList.add(item)
        }
    }

    Collections.sort(fileList)
    return fileList
}

fun getTimeStringFromMillis(millis: Long): String {
    val millisAsLong = millis.toLong()

    val minutes = TimeUnit.MILLISECONDS.toMinutes(millisAsLong)
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millisAsLong)
    val displaySeconds = totalSeconds - TimeUnit.MINUTES.toSeconds(minutes)

    return "%d:%02d".format(minutes, displaySeconds)
}