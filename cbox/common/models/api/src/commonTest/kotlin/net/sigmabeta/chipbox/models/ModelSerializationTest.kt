package net.sigmabeta.chipbox.models

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Round-trips every wire type through Json. Catches the two real risks:
 *  - `Platform` enum has a `SageStringId` constructor arg; if the serializer can't ignore that,
 *    the whole API is broken before it starts.
 *  - `Track ↔ Game ↔ Artist` back-links are nullable; deserializing a structure with a `null`
 *    back-link (which is the contract the HTTP routes enforce) needs to round-trip cleanly.
 */
class ModelSerializationTest {
    private val json = Json { prettyPrint = false }

    @Test
    fun platform_enum_round_trips_by_name() {
        val encoded = json.encodeToString(Platform.NES)
        assertEquals("\"NES\"", encoded)
        assertEquals(Platform.NES, json.decodeFromString<Platform>(encoded))
    }

    @Test
    fun fully_populated_track_round_trips_with_back_links_nulled() {
        val track = Track(
            id = 42L,
            path = "/library/Mega Man 2/01 Title.nsf",
            source = "local",
            title = "Title Theme",
            trackLengthMs = 90_000L,
            trackNumber = 1,
            fadeLengthMs = FADE_LENGTH_MS,
            game = Game(
                id = 7L,
                title = "Mega Man 2",
                photoUrl = "https://example.com/mm2.png",
                artists = null,
                tracks = null,
            ),
            artists = listOf(
                Artist(id = 3L, name = "Takashi Tateishi", photoUrl = null, tracks = null, games = null),
            ),
            chainFiles = listOf(ChainFile(filename = "soundbank.bin", uri = "/library/soundbank.bin")),
            extension = "nsf",
            platform = Platform.NES,
        )
        val encoded = json.encodeToString(track)
        assertTrue(encoded.contains("\"platform\":\"NES\""), "Platform should serialize as enum name")
        assertEquals(track, json.decodeFromString<Track>(encoded))
    }

    @Test
    fun nullable_back_links_serialize_cleanly() {
        val game = Game(id = 1L, title = "Tetris", photoUrl = null, artists = null, tracks = null)
        assertEquals(game, json.decodeFromString<Game>(json.encodeToString(game)))
    }

    @Test
    fun search_history_round_trips() {
        val entry = SearchHistory(id = 100L, query = "mega man")
        assertEquals(entry, json.decodeFromString<SearchHistory>(json.encodeToString(entry)))
    }
}
