package net.sigmabeta.chipbox.ui.previews.fake

import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.sage.ui.StringGenerator
import java.util.Random

/**
 * Deterministic domain-model generator for screenshot previews — Chipbox's analogue of VGLS's
 * `FakeModelGenerator`. Self-contained: given a [seed], every call produces identical
 * [Game]/[Track]/[Artist] data, so Paparazzi snapshots are stable across runs.
 *
 * It owns its [Random]/[StringGenerator] so feature `src/debug` callers never need a direct
 * dependency on `net.sigmabeta.sage.ui.StringGenerator`.
 */
@Suppress("MagicNumber")
class FakeModelGenerator(private val seed: Long = DEFAULT_SEED) {
    private val random = Random(seed)
    private val stringGenerator = StringGenerator(random)

    fun loadingName(): String {
        random.setSeed(seed)
        return stringGenerator.generateName()
    }

    fun randomArtists(): List<Artist> {
        random.setSeed(seed)
        val count = random.nextInt(2) + 2 // 2..3
        return (0 until count).map { index ->
            Artist(
                id = index + 1L,
                name = stringGenerator.generateName(),
                photoUrl = "preview://artist/$index",
                tracks = null,
                games = null,
            )
        }
    }

    fun randomGames(): List<Game> {
        random.setSeed(seed + 3)
        val count = random.nextInt(2) + 3 // 3..4
        return (0 until count).map { index ->
            Game(
                id = index + 1L,
                title = stringGenerator.generateTitle(),
                photoUrl = "preview://game/$index",
                artists = null,
                tracks = null,
            )
        }
    }

    fun randomTracks(artists: List<Artist>): List<Track> =
        buildTracks(seed + 1, artists, games = null)

    /** Tracks tagged with a [Game] so artist-detail rows show a non-empty caption. */
    fun randomTracks(artists: List<Artist>, games: List<Game>): List<Track> =
        buildTracks(seed + 4, artists, games)

    fun randomGame(artists: List<Artist>, tracks: List<Track>): Game {
        random.setSeed(seed + 2)
        return Game(
            id = 1L,
            title = stringGenerator.generateTitle(),
            photoUrl = "preview://game/cover",
            artists = artists,
            tracks = tracks,
        )
    }

    private fun buildTracks(
        trackSeed: Long,
        artists: List<Artist>,
        games: List<Game>?,
    ): List<Track> {
        random.setSeed(trackSeed)
        val count = random.nextInt(8) + 8 // 8..15
        return (0 until count).map { index ->
            Track(
                id = index + 1L,
                path = "preview://track/$index",
                source = "",
                title = stringGenerator.generateTitle(),
                trackLengthMs = (random.nextInt(360) + 30) * 1_000L,
                trackNumber = index + 1,
                fadeLengthMs = 0L,
                game = games?.get(index % games.size),
                artists = listOf(artists[index % artists.size]),
                platform = Platform.OTHER,
            )
        }
    }

    companion object {
        const val DEFAULT_SEED = 1234L
    }
}
