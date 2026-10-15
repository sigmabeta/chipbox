package net.sigmabeta.chipbox.benchmark

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Picks the artist the artist-detail scroll scenario should open, from the library snapshot the run
 * is seeded from (and pinned to). Opening the *richest* artist — rather than whatever the grid's
 * first tile happens to be — guarantees the detail page has a long song list and therefore scrolls;
 * the alphabetically-first artist can have a single track, whose page fits on screen and exposes no
 * scrollable node to measure.
 *
 * The snapshot is parsed leniently: only `games[].tracks[].artist` is read, so scanner-schema changes
 * elsewhere do not break the benchmark.
 */
internal object BenchmarkArtists {

    /** The repository's artist-name delimiters (`DatabaseRepository`), so counts match the app. */
    private val DELIMITERS_ARTISTS = Regex(", &|,| or | and |&")

    private val json = Json

    /**
     * The name of the artist credited on the most tracks in [snapshot], ties broken by name so the
     * choice is deterministic; `null` when [snapshot] is missing or carries no credited artist.
     */
    fun richestArtistName(snapshot: File): String? {
        if (!snapshot.isFile) return null
        val games = runCatching {
            json.parseToJsonElement(snapshot.readText()).jsonObject["games"]?.jsonArray
        }.getOrNull() ?: return null

        val tracksByArtist = LinkedHashMap<String, Int>()
        for (game in games) {
            val tracks = game.jsonObject["tracks"]?.jsonArray ?: continue
            for (track in tracks) {
                val artist = (track.jsonObject["artist"] as? JsonPrimitive)?.contentOrNull ?: continue
                // A multi-artist track credits each name once, matching the repository's derivation.
                for (name in artist.split(DELIMITERS_ARTISTS).map { it.trim() }.filter { it.isNotEmpty() }.toSet()) {
                    tracksByArtist[name] = (tracksByArtist[name] ?: 0) + 1
                }
            }
        }
        return tracksByArtist.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()
            ?.key
    }
}
