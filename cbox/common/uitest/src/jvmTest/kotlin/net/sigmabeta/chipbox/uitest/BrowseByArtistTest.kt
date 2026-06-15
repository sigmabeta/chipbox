package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.artistdetail.ArtistDetail
import net.sigmabeta.chipbox.features.browsebyartist.BrowseByArtist
import kotlin.test.Test

/** Browse-by-artist lists every artist; tapping one opens its detail screen. */
class BrowseByArtistTest {
    @Test
    fun listsArtists() = runChipboxUiTest {
        startAtScreen(BrowseByArtist)

        assertGridImageItemDisplayed("Jake Shimomura")
    }

    @Test
    fun tappingArtistOpensDetail() = runChipboxUiTest {
        startAtScreen(BrowseByArtist)

        click("Jake Shimomura")

        assertNavigationEvent(ArtistDetail(artistId("Jake Shimomura")))
    }
}
