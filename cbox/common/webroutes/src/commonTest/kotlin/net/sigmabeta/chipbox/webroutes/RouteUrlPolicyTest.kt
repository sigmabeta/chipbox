package net.sigmabeta.chipbox.webroutes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.sigmabeta.chipbox.capabilities.AppCapabilities
import net.sigmabeta.chipbox.features.artistdetail.ArtistDetail
import net.sigmabeta.chipbox.features.browsealltracks.BrowseAllTracks
import net.sigmabeta.chipbox.features.browsebyartist.BrowseByArtist
import net.sigmabeta.chipbox.features.browsebygame.BrowseByGame
import net.sigmabeta.chipbox.features.browsebyplatform.BrowseByPlatform
import net.sigmabeta.chipbox.features.componentlibrary.ComponentLibrary
import net.sigmabeta.chipbox.features.componentlibrary.ComponentLibraryMode
import net.sigmabeta.chipbox.features.componentlibrary.LibraryMode
import net.sigmabeta.chipbox.features.crashlog.CrashLog
import net.sigmabeta.chipbox.features.errorlog.ErrorLog
import net.sigmabeta.chipbox.features.favorites.Favorites
import net.sigmabeta.chipbox.features.folderpicker.FolderPicker
import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import net.sigmabeta.chipbox.features.gamesforplatform.GamesForPlatform
import net.sigmabeta.chipbox.features.managelibrary.ManageLibrary
import net.sigmabeta.chipbox.features.nowplaying.NowPlaying
import net.sigmabeta.chipbox.features.playbackstatus.PlaybackStatus
import net.sigmabeta.chipbox.features.playlistdetail.PlaylistDetail
import net.sigmabeta.chipbox.features.playlists.Playlists
import net.sigmabeta.chipbox.features.rescanstatus.RescanStatus
import net.sigmabeta.chipbox.features.search.Search
import net.sigmabeta.chipbox.features.settings.Settings
import net.sigmabeta.chipbox.features.tracksforartist.TracksForArtist
import net.sigmabeta.chipbox.models.Platform

/**
 * The URL surface is the web shell's route set, not every destination the shared UI can push.
 * Admin surfaces (05 hides them) and debug-only screens have no URL; the Error Log is the exception
 * (it is browser-local and reachable under Settings > Diagnostics).
 */
class RouteUrlPolicyTest {

    private val webCapabilities = AppCapabilities(
        canManageLibrary = false,
        showDebugSection = false,
        showErrorLog = true,
    )

    @Test
    fun `admin only destinations never encode`() {
        listOf(ManageLibrary, FolderPicker, RescanStatus).forEach {
            assertNull(RouteUrl.encode(ChipboxTarget.Deep(it)), "$it must not be encodable")
        }
    }

    @Test
    fun `debug only destinations never encode`() {
        listOf(
            PlaybackStatus,
            CrashLog,
            ComponentLibrary,
            ComponentLibraryMode(LibraryMode.LIST),
            ComponentLibraryMode(LibraryMode.GRID),
        ).forEach {
            assertNull(RouteUrl.encode(ChipboxTarget.Deep(it)), "$it must not be encodable")
        }
    }

    @Test
    fun `admin and debug paths are unknown, so the host falls back to the root`() {
        listOf(
            "/manage-library",
            "/folder-picker",
            "/rescan-status",
            "/playback-status",
            "/crash-log",
            "/component-library",
            "/component-library/LIST",
            "/library/manage",
            "/library/rescan",
        ).forEach {
            assertNull(RouteUrl.decode(it, webCapabilities), "$it must not decode")
        }
    }

    @Test
    fun `the error log is reachable as a settings path when the host exposes it`() {
        assertEquals("/settings/errors", RouteUrl.encode(ChipboxTarget.Deep(ErrorLog)))
        assertEquals(ErrorLog, (RouteUrl.decode("/settings/errors", webCapabilities) as ChipboxTarget.Deep).destination)
    }

    @Test
    fun `the error log path is unknown when the host withholds it`() {
        assertNull(RouteUrl.decode("/settings/errors", AppCapabilities(showErrorLog = false)))
    }

    @Test
    fun `the shell and the codec agree on what may be navigated to`() {
        val full = AppCapabilities.Default
        listOf(ManageLibrary, FolderPicker, RescanStatus).forEach {
            assertEquals(true, full.allowsNavigation(it))
            assertEquals(false, webCapabilities.allowsNavigation(it))
        }
        listOf(PlaybackStatus, CrashLog, ComponentLibrary, ComponentLibraryMode(LibraryMode.LIST)).forEach {
            assertEquals(true, full.allowsNavigation(it))
            assertEquals(false, webCapabilities.allowsNavigation(it))
        }
        assertEquals(true, webCapabilities.allowsNavigation(ErrorLog))
        assertEquals(false, AppCapabilities(showErrorLog = false).allowsNavigation(ErrorLog))
        assertEquals(true, webCapabilities.allowsNavigation(GameDetail(1L)))
    }

    @Test
    fun `every encodable destination round-trips`() {
        val destinations = listOf(
            Favorites,
            Playlists(),
            PlaylistDetail(1L),
            BrowseByGame,
            BrowseByArtist,
            BrowseByPlatform,
            BrowseAllTracks,
            GameDetail(2L),
            ArtistDetail(3L),
            TracksForArtist(4L, "Nobuo Uematsu"),
            GamesForPlatform(Platform.DREAMCAST),
            NowPlaying,
            Settings,
            ErrorLog,
        )

        destinations.forEach { destination ->
            val target = ChipboxTarget.Deep(destination)
            val url = RouteUrl.encode(target)
            requireNotNull(url) { "$destination should be encodable" }
            assertEquals(target, RouteUrl.decode(url, webCapabilities), "round-trip for $destination via $url")
        }
    }

    @Test
    fun `tab-root destinations canonicalise to the tab path`() {
        val cased = listOf(
            net.sigmabeta.chipbox.features.home.Home to ChipboxTab.HOME,
            net.sigmabeta.chipbox.features.library.Library to ChipboxTab.LIBRARY,
            Search to ChipboxTab.SEARCH,
        )
        cased.forEach { (destination, tab) ->
            val url = RouteUrl.encode(ChipboxTarget.Deep(destination))
            assertEquals(RouteUrl.encode(ChipboxTarget.Tab(tab)), url)
            assertEquals(ChipboxTarget.Tab(tab), RouteUrl.decode(url!!, webCapabilities))
        }
    }

    @Test
    fun `unknown and malformed paths do not decode`() {
        listOf(
            "/nope",
            "/game/42/extra",
            "/library/playlists/7/extra",
            "/settings/errors/extra",
            "//",
            "/library//favorites",
            "game/42",
        ).forEach { assertNull(RouteUrl.decode(it, webCapabilities), "$it must not decode") }
    }

    @Test
    fun `absolute urls and fragments are accepted`() {
        assertEquals(GameDetail(42L), (RouteUrl.decode("https://chipbox.lan/game/42") as ChipboxTarget.Deep).destination)
        assertEquals(
            GameDetail(42L),
            (RouteUrl.decode("http://chipbox.lan/game/42#top") as ChipboxTarget.Deep).destination,
        )
    }

    @Test
    fun `an unknown destination does not encode`() {
        assertNull(RouteUrl.encode(ChipboxTarget.Deep("not a route")))
    }
}
