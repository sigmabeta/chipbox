package net.sigmabeta.chipbox.cli

import net.sigmabeta.chipbox.models.Platform
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Reads and writes a small, human-readable `igdb.txt` in a game's folder describing which IGDB game
 * its cover art was matched to: the IGDB name and id, a link to the igdb.com page, the cover image id
 * (absent when the matched game has no cover on IGDB), and the date the match was made. It sits next
 * to the downloaded `<Title>.jpg` so anyone browsing the folder can see — and double-check — where
 * the cover came from, and a later "Get cover art" run can [read] it back to skip the IGDB query for
 * a game whose folder already records its match.
 *
 * A descriptor counts as a usable record only while its "Matched" date is within [COVER_ART_TTL_DAYS]
 * — the same window the [CoverArtCache] uses — so a long-stale file is re-queried rather than trusted
 * forever (letting covers IGDB has since added get picked up). The date is therefore "when the match
 * was last confirmed against IGDB": [writeInto] refreshes it only on a fresh lookup, and otherwise
 * keeps the existing one so re-confirming from the cache or this file doesn't churn it.
 *
 * A write that fails for one folder (e.g. read-only) is skipped without affecting the others or the
 * cover download itself.
 */
object IgdbLinkFile {
    const val FILE_NAME = "igdb.txt"

    /**
     * Reconstructs the match recorded in the first of [folders] that holds a descriptor still within
     * the [COVER_ART_TTL_DAYS] window; a missing, unreadable, or long-stale file yields null so the
     * caller falls back to querying IGDB.
     */
    fun read(folders: List<File>, today: LocalDate = LocalDate.now()): CoverLookup? =
        folders.firstNotNullOfOrNull { readOne(File(it, FILE_NAME), today) }

    /**
     * Writes the descriptor into each of [folders]. [refreshDate] dates it today (use it when [match]
     * came from a fresh IGDB lookup); otherwise the existing date is kept for an unchanged match so
     * re-confirming from the cache or the file itself doesn't move it.
     */
    fun writeInto(
        folders: List<File>,
        title: String,
        platforms: Set<Platform>,
        match: CoverLookup.Matched,
        refreshDate: Boolean,
        today: LocalDate = LocalDate.now(),
    ) {
        val body = bodyLines(title, platforms, match)
        for (folder in folders) {
            runCatching { writeOne(File(folder, FILE_NAME), body, refreshDate, today) }
        }
    }

    // Parses a descriptor back into the match it records: a "Cover" line means Found, an IGDB game
    // without one means NoCover, and anything we can't recognise as ours (or that has aged past the
    // TTL) is treated as absent (null).
    private fun readOne(file: File, today: LocalDate): CoverLookup? {
        val lines = file.takeIf { it.isFile }
            ?.let { runCatching { it.readText() }.getOrNull() }
            ?.lines()
            ?.takeUnless { isStale(it, today) }
            ?: return null
        val imageId = value(lines, COVER_LABEL)
        val (igdbName, igdbId) = igdbNameAndId(value(lines, IGDB_LABEL))
        val slug = value(lines, URL_LABEL)?.substringAfterLast('/')?.ifBlank { null }
        return when {
            imageId != null -> CoverLookup.Found(imageId, igdbId, igdbName, slug)
            igdbId != null || igdbName != null -> CoverLookup.NoCover(igdbId, igdbName, slug)
            else -> null
        }
    }

    private fun writeOne(file: File, body: List<String>, refreshDate: Boolean, today: LocalDate) {
        val kept = if (refreshDate) null else unchangedDate(file, body)
        val content = (body + line(MATCHED_LABEL, kept ?: today.toString())).joinToString("\n") + "\n"
        if (!file.isFile || file.readText() != content) file.writeText(content)
    }

    // True when the descriptor's "Matched" date is missing, unparseable, or older than the TTL — i.e.
    // the recorded match shouldn't be trusted without re-checking IGDB.
    private fun isStale(lines: List<String>, today: LocalDate): Boolean {
        val date = value(lines, MATCHED_LABEL)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return date == null || ChronoUnit.DAYS.between(date, today) > COVER_ART_TTL_DAYS
    }

    // If [file] already describes this exact match, returns its recorded date so it isn't bumped to
    // today; otherwise null, meaning the descriptor is new or has changed and should be dated today.
    private fun unchangedDate(file: File, body: List<String>): String? {
        if (!file.isFile) return null
        val lines = file.readText().trimEnd('\n').lines()
        val matched = lines.lastOrNull()?.takeIf { it.startsWith(MATCHED_LABEL) }
        return matched?.removePrefix(MATCHED_LABEL)?.trim()?.takeIf { lines.dropLast(1) == body }
    }

    private fun bodyLines(
        title: String,
        platforms: Set<Platform>,
        match: CoverLookup.Matched,
    ): List<String> = buildList {
        add(HEADER)
        add("")
        add(line(GAME_LABEL, title))
        igdbLabel(match)?.let { add(line(IGDB_LABEL, it)) }
        gameUrl(match)?.let { add(line(URL_LABEL, it)) }
        platformList(platforms)?.let { add(line(PLATFORMS_LABEL, it)) }
        (match as? CoverLookup.Found)?.let { add(line(COVER_LABEL, it.imageId)) }
    }

    private fun line(label: String, value: String): String = label.padEnd(LABEL_WIDTH) + value

    private fun gameUrl(match: CoverLookup.Matched): String? =
        match.igdbSlug?.takeIf { it.isNotBlank() }?.let { "$IGDB_GAME_URL_BASE/$it" }

    private fun value(lines: List<String>, label: String): String? =
        lines.firstOrNull { it.startsWith(label) }?.removePrefix(label)?.trim()?.ifBlank { null }

    private fun igdbLabel(match: CoverLookup.Matched): String? {
        val name = match.igdbName?.takeIf { it.isNotBlank() }
        val id = match.igdbId?.takeIf { it.isNotBlank() }
        return when {
            name != null && id != null -> "$name (#$id)"
            name != null -> name
            id != null -> "#$id"
            else -> null
        }
    }

    // Splits an "IGDB" value like "Sonic the Hedgehog (#1234)" back into its (name, id).
    private fun igdbNameAndId(value: String?): Pair<String?, String?> {
        val match = value?.let { IGDB_VALUE_PATTERN.matchEntire(it) }
        return when {
            match != null -> match.groupValues[1] to match.groupValues[2]
            else -> value to null
        }
    }

    private fun platformList(platforms: Set<Platform>): String? =
        platforms.map { it.name }.sorted().joinToString(", ").ifBlank { null }

    private const val LABEL_WIDTH = 11
    private const val GAME_LABEL = "Game:"
    private const val IGDB_LABEL = "IGDB:"
    private const val URL_LABEL = "URL:"
    private const val PLATFORMS_LABEL = "Platforms:"
    private const val COVER_LABEL = "Cover:"
    private const val MATCHED_LABEL = "Matched:"
    private const val IGDB_GAME_URL_BASE = "https://www.igdb.com/games"
    private val IGDB_VALUE_PATTERN = Regex("""^(.*) \(#(.+)\)$""")
    private const val HEADER =
        "# Chipbox IGDB link — describes this folder's cover-art match. Safe to delete."
}
