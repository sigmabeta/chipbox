package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.artistdetail.ArtistDetail
import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import kotlin.test.Test

/**
 * The detail screens build their section headers from real string resources (via the preloaded
 * [net.sigmabeta.chipbox.strings.real.ChipboxStringProvider]), so assert each header reads what it
 * should: GameDetail → "Songs" + "Artists"; ArtistDetail → "Songs" + "Games".
 */
class SectionHeaderTest {
    @Test
    fun gameDetailShowsSongAndArtistHeaders() = runChipboxUiTest {
        startAtScreen(GameDetail(gameId("Iron Quest")))

        assertSectionHeader("Songs")
        assertSectionHeader("Artists")
    }

    @Test
    fun artistDetailShowsSongAndGameHeaders() = runChipboxUiTest {
        // Jake Shimomura is an artist of "Silent Saga", so they have both games and songs.
        startAtScreen(ArtistDetail(artistId("Jake Shimomura")))

        assertSectionHeader("Songs")
        assertSectionHeader("Games")
    }
}
