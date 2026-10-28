package net.sigmabeta.chipbox.webroutes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.sigmabeta.chipbox.features.artistdetail.ArtistDetail
import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import net.sigmabeta.chipbox.features.gamesforplatform.GamesForPlatform
import net.sigmabeta.chipbox.features.favorites.Favorites
import net.sigmabeta.chipbox.features.nowplaying.NowPlaying
import net.sigmabeta.chipbox.features.settings.Settings
import net.sigmabeta.chipbox.features.tracksforartist.TracksForArtist
import net.sigmabeta.chipbox.models.Platform

/**
 * Detail screens and their typed arguments. Ids live in the path; the artist's display name is a
 * query parameter because it is free text (and only cosmetic — the screen refetches by id).
 */
class RouteUrlDetailTest {

    @Test
    fun `detail destinations encode to their documented paths`() {
        assertEquals("/game/42", encode(GameDetail(42L)))
        assertEquals("/artist/9", encode(ArtistDetail(9L)))
        assertEquals("/artist/9/tracks?name=Daisuke%20Ishiwatari", encode(TracksForArtist(9L, "Daisuke Ishiwatari")))
        assertEquals("/platform/SNES/games", encode(GamesForPlatform(Platform.SNES)))
        assertEquals("/now-playing", encode(NowPlaying))
        assertEquals("/settings", encode(Settings))
    }

    @Test
    fun `detail paths decode to their destinations`() {
        assertEquals(GameDetail(42L), decode("/game/42"))
        assertEquals(ArtistDetail(9L), decode("/artist/9"))
        assertEquals(TracksForArtist(9L, "Daisuke Ishiwatari"), decode("/artist/9/tracks?name=Daisuke%20Ishiwatari"))
        assertEquals(GamesForPlatform(Platform.SNES), decode("/platform/SNES/games"))
        assertEquals(NowPlaying, decode("/now-playing"))
        assertEquals(Settings, decode("/settings"))
    }

    @Test
    fun `platform names decode case-insensitively`() {
        assertEquals(GamesForPlatform(Platform.SNES), decode("/platform/snes/games"))
        assertEquals(GamesForPlatform(Platform.GAMEBOY_ADVANCE), decode("/platform/gameboy_advance/games"))
        assertEquals(GamesForPlatform(Platform.N64), decode("/platform/N64/games"))
    }

    @Test
    fun `an unknown platform does not decode`() {
        assertNull(decode("/platform/not-a-platform/games"))
        assertNull(decode("/platform//games"))
    }

    @Test
    fun `artist names round-trip through percent encoding`() {
        val names = listOf(
            "Daisuke Ishiwatari",
            "A+B",
            "100%",
            "坂本 龍一",
            "Hirokazu \"Hip\" Tanaka",
            "a/b?c&d=e",
        )
        names.forEach { name ->
            val encoded = RouteUrl.encode(ChipboxTarget.Deep(TracksForArtist(3L, name)))!!
            assertEquals(TracksForArtist(3L, name), decode(encoded), "round-trip for '$name' via $encoded")
        }
    }

    @Test
    fun `a missing artist name is tolerated as an empty title`() {
        assertEquals(TracksForArtist(3L, ""), decode("/artist/3/tracks"))
        assertEquals(TracksForArtist(3L, ""), decode("/artist/3/tracks?api=http://localhost:8080"))
    }

    @Test
    fun `query overrides for the api base url are ignored, not treated as route arguments`() {
        assertEquals(GameDetail(42L), decode("/game/42?api=http://localhost:8080"))
        assertEquals(TracksForArtist(9L, "Nobuo Uematsu"), decode("/artist/9/tracks?name=Nobuo%20Uematsu&api=http://board:8080"))
    }

    @Test
    fun `ids must be positive`() {
        listOf("/game/0", "/game/-1", "/game/abc", "/game/1.5", "/artist/0", "/artist/-1", "/artist/x").forEach {
            assertNull(decode(it), "$it should not decode")
        }
        assertNull(RouteUrl.encode(ChipboxTarget.Deep(GameDetail(-2L))))
        assertNull(RouteUrl.encode(ChipboxTarget.Deep(ArtistDetail(0L))))
        assertNull(RouteUrl.encode(ChipboxTarget.Deep(TracksForArtist(-1L, "x"))))
    }

    @Test
    fun `trailing slashes are canonicalised`() {
        assertEquals(GameDetail(42L), decode("/game/42/"))
        assertEquals(Favorites, decode("/library/favorites/"))
        assertEquals(GamesForPlatform(Platform.PSX), decode("/platform/PSX/games/"))
    }

    private fun encode(destination: Any): String? = RouteUrl.encode(ChipboxTarget.Deep(destination))

    private fun decode(path: String): Any? = (RouteUrl.decode(path) as? ChipboxTarget.Deep)?.destination
}
