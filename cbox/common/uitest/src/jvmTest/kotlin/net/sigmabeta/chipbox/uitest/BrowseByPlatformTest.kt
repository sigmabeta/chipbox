package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.browsebyplatform.BrowseByPlatform
import net.sigmabeta.chipbox.features.gamesforplatform.GamesForPlatform
import net.sigmabeta.chipbox.models.Platform
import kotlin.test.Test

/**
 * Browse-by-platform lists the platforms present in the library; tapping one opens its games. The
 * in-memory fake tags every track as [Platform.OTHER] ("Other"), so that's the single platform row.
 */
class BrowseByPlatformTest {
    @Test
    fun listsPlatforms() = runChipboxUiTest {
        startAtScreen(BrowseByPlatform)

        assertIconNameItemDisplayed("Other")
    }

    @Test
    fun tappingPlatformOpensItsGames() = runChipboxUiTest {
        startAtScreen(BrowseByPlatform)

        click("Other")

        assertNavigationEvent(GamesForPlatform(Platform.OTHER))
    }
}
