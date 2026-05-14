package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.utils.convert
import net.sigmabeta.sage.logging.Hatchet

const val EXTENSION_M3U = "m3u"

data class M3uEntry(
    val filename: String,
    val trackNumber: Int,  // 0-based
    val title: String,
    val artist: String?,   // non-null only in GBS-style compound tags ("Title - Artist - Game")
    val game: String?,     // non-null only in GBS-style compound tags
    val lengthMs: Long,
    val fadeLengthMs: Long,
)

class M3uReader(private val hatchet: Hatchet) {
    fun parse(bytes: ByteArray): List<M3uEntry> {
        return try {
            bytes.convert()
                .split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("::") }
                .mapNotNull { it.toM3uEntry() }
        } catch (ex: Exception) {
            hatchet.w("Failed to parse m3u: ${ex.message}")
            emptyList()
        }
    }
}

private fun String.toM3uEntry(): M3uEntry? {
    val filename = substringBefore("::")
    val tags = substringAfter("::").splitByUnescapedCommas()

    // tags[0] is the source format (GBS/NSF/etc.), tags[1] is the subtune index. Conventions
    // differ: NSF / NSFE / PSF m3u files index from 1 (matching the format's 1-based "song
    // number"), but GBS m3u files in the wild are 0-based (matching the GBS internal subtune
    // index). Normalize to our readers' 0-based trackNumber.
    val format = tags.getOrNull(0)?.uppercase()
    val rawIndex = tags.getOrNull(1)?.toIntOrNull() ?: return null
    val trackNumber = if (format == "GBS") rawIndex else rawIndex - 1
    if (trackNumber < 0) return null

    val rawMeta = tags.getOrNull(2) ?: TAG_UNKNOWN
    val metaParts = rawMeta.split(" - ")
    val title: String
    val artist: String?
    val game: String?
    if (metaParts.size >= 4 && metaParts.last().looksLikeCopyright()) {
        // Zophar GBS/NSF compound: "Title - Artist - Game - Copyright". Anchor from the right
        // so titles containing " - " (e.g. "Stage 3 - Float Islands") survive intact.
        title = metaParts.dropLast(3).joinToString(" - ").orUnknown()
        artist = metaParts[metaParts.size - 3].orUnknown()
        game = metaParts[metaParts.size - 2].orUnknown()
    } else if (metaParts.size == 3) {
        title = metaParts[0].orUnknown()
        artist = metaParts[1].orUnknown()
        game = metaParts[2].orUnknown()
    } else {
        title = rawMeta.orUnknown()
        artist = null
        game = null
    }

    val lengthMs = tags.getOrNull(3)?.toLengthMillis() ?: LENGTH_UNKNOWN_MS
    val fadeLengthMs = (tags.getOrNull(5)?.toLengthMillis() ?: 0L).coerceAtLeast(0L)

    return M3uEntry(filename, trackNumber, title, artist, game, lengthMs, fadeLengthMs)
}

/** Splits on commas not preceded by a backslash, then strips escape characters. */
private fun String.splitByUnescapedCommas() = split(Regex("(?<!\\\\),"))
    .map { it.filterNot { c -> c == '\\' } }

/**
 * The trailing field of a Zophar GBS/NSF tag is a copyright line like "©1992 HAL Laboratory" —
 * sometimes mangled to "�..." by an upstream encoding error. Either the copyright sigil or a
 * 4-digit year is a strong enough signal to anchor right-side parsing.
 */
private fun String.looksLikeCopyright(): Boolean =
    contains('©') || contains('�') || Regex("""\b(19|20)\d{2}\b""").containsMatchIn(this)
