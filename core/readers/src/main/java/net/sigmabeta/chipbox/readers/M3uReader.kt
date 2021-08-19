package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.chipbox.utils.convert
import timber.log.Timber
import java.io.File
import java.io.UnsupportedEncodingException

object M3uReader : Reader() {
    override fun readTracksFromFile(path: String): List<RawTrack>? {
        val fileAsByteBuffer = fileAsByteBuffer(path)

        try {
            val fileAsString = fileAsByteBuffer
                .array()
                .convert()

            val nsfTracks = fileAsString.searchForNsfTracks(path)
            val gbsTracks = fileAsString.searchForGbsTracks(path)

            return nsfTracks + gbsTracks
        } catch (iae: IllegalArgumentException) {
            Timber.e("Illegal argument: ${iae.message}")
            return null
        } catch (e: UnsupportedEncodingException) {
            Timber.e("Unsupported Encoding: ${e.message}")
            return null
        } catch (e: Exception) {
            Timber.e("Malformed m3u file: $path")
            return null
        }
    }
}

private fun String.searchForNsfTracks(path: String): List<RawTrack> {
    val nsfLines = split("\n".toRegex())
        .map { it.trim() }
        .filter { it.contains("NSF") }

    if (nsfLines.isEmpty()) {
        return emptyList()
    }

    return nsfLines
        .map { it.toRawNsfTrack(path) }
        .filterNotNull()
}

private fun String.searchForGbsTracks(path: String): List<RawTrack> {
    val nsfLines = split("\n".toRegex())
        .map { it.trim() }
        .filter { it.contains("GBS") }

    if (nsfLines.isEmpty()) {
        return emptyList()
    }

    return nsfLines
        .map { it.toRawGbsTrack(path) }
        .filterNotNull()
}

private fun String.toRawNsfTrack(pathToM3u: String): RawTrack? {
    val parent = File(pathToM3u).parent
    if (parent == null) {
        Timber.e("File doesn't have a parent, somehow.")
        return null
    }

    val filename = substringBefore("::")

    val tags = splitByUnescapedCommas()

    return RawTrack(
        "$parent/$filename",
        tags[2],
        TAG_UNKNOWN,
        TAG_UNKNOWN,
        tags[3].toLengthMillis(),
        tags[5].toLengthMillis() > 0
    )
}

private fun String.toRawGbsTrack(pathToM3u: String): RawTrack? {
    val parent = File(pathToM3u).parent
    if (parent == null) {
        Timber.e("File doesn't have a parent, somehow.")
        return null
    }

    val filename = substringBefore("::")

    val tags = splitByUnescapedCommas()

    val whereGbsFilesHaveAllTheRelevantInfo = tags[2]
    val actualTags = whereGbsFilesHaveAllTheRelevantInfo
        .split(" - ")

    return RawTrack(
        "$parent/$filename",
        actualTags[0],
        actualTags[1],
        actualTags[2],
        tags[3].toLengthMillis(),
        tags[5].toLengthMillis() > 0
    )
}

/** Matches a comma without a backslash before it.
 * Why 4 backslashes? Simple:
 *  - The first is the one we want to ignore, of course.
 *  - The second escapes it for regex processing, because it is an escape in regex.
 *  - Each of the above needs to *itself* be escaped in a Kotlin string.
 *  */
private fun String.splitByUnescapedCommas() = substringAfter("::")
    .split(Regex("(?<!\\\\),"))
    .map {
        it.filterNot { it == '\\' }
    }