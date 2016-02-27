package net.sigmabeta.chipbox.util

import net.sigmabeta.chipbox.model.file.FileListItem
import net.sigmabeta.chipbox.model.objects.Track
import net.sigmabeta.chipbox.util.external.getFileInfoNative
import net.sigmabeta.chipbox.util.external.getPlatformNative
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

val GME_PLATFORM_SNES = "Super Nintendo"
val GME_PLATFORM_GENESIS = "Sega SMS/Genesis"

val TYPE_OTHER = -1
val TYPE_FOLDER = 0

val INDEX_TRACK_INFO_LENGTH = 0
val INDEX_TRACK_INFO_INTRO_LENGTH = 1
val INDEX_TRACK_INFO_LOOP_LENGTH = 2
val INDEX_TRACK_INFO_TITLE = 3
val INDEX_TRACK_INFO_GAME = 4
val INDEX_TRACK_INFO_SYSTEM = 5
val INDEX_TRACK_INFO_ARTIST = 6

val EXTENSIONS_MUSIC: HashSet<String> = HashSet(arrayListOf(
        ".spc", ".vgm", ".vgz")
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

fun readTrackInfoFromPath(path: String, trackNumber: Int): Track? {
    val info = getFileInfoNative(path, trackNumber) ?: return null

    val title = info.get(INDEX_TRACK_INFO_TITLE)

    val platform: Int
    when (info.get(INDEX_TRACK_INFO_SYSTEM)) {
        "Super Nintendo" -> platform = Track.PLATFORM_SNES
        "Sega Mega Drive", "Sega Mega Drive / Genesis", "Sega Genesis" -> platform = Track.PLATFORM_GENESIS
        else -> return null
    }

    val gameTitle = info.get(INDEX_TRACK_INFO_GAME)
    val artist = info.get(INDEX_TRACK_INFO_ARTIST)

    val trackLength = info.get(INDEX_TRACK_INFO_LENGTH).toLong()
    val introLength = info.get(INDEX_TRACK_INFO_INTRO_LENGTH).toLong()
    val loopLength = info.get(INDEX_TRACK_INFO_LOOP_LENGTH).toLong()

    return Track(
            -1,
            trackNumber,
            path,
            title,
            -1,
            gameTitle,
            platform,
            artist,
            trackLength,
            introLength,
            loopLength
    )
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