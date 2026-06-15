package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.browsealltracks.BrowseAllTracks
import net.sigmabeta.chipbox.features.browsebyartist.BrowseByArtist
import net.sigmabeta.chipbox.features.browsebygame.BrowseByGame
import net.sigmabeta.chipbox.features.library.Library
import kotlin.test.Test

/**
 * Library is a static menu of "browse by …" rows; each navigates to the matching browse screen.
 * (Browse by Platform is omitted — its screen depends on repository platform queries that are still
 * `TODO` in the in-memory fake, so navigating there would crash on render.)
 */
class LibraryTest {
    @Test
    fun showsBrowseMenu() = runChipboxUiTest {
        startAtScreen(Library)

        assertDisplayed("Browse by Game")
        assertDisplayed("Browse by Artist")
        assertDisplayed("Browse All Tracks")
    }

    @Test
    fun browseByGameRowNavigates() = runChipboxUiTest {
        startAtScreen(Library)

        click("Browse by Game")

        assertNavigationEvent(BrowseByGame)
    }

    @Test
    fun browseByArtistRowNavigates() = runChipboxUiTest {
        startAtScreen(Library)

        click("Browse by Artist")

        assertNavigationEvent(BrowseByArtist)
    }

    @Test
    fun browseAllTracksRowNavigates() = runChipboxUiTest {
        startAtScreen(Library)

        click("Browse All Tracks")

        assertNavigationEvent(BrowseAllTracks)
    }
}
