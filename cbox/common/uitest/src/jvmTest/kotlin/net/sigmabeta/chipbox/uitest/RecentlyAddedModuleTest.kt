package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import kotlin.test.Test

/**
 * The "Recently added" Home row (RecentlyAddedGamesHomeModule). The seeded library stamps every
 * game's dateAdded when it's added, so all fall inside the one-week window and the row renders a
 * carousel of game covers. The Home tab is already hosted by the shell, so no navigation is needed.
 *
 * The carousel's order is randomised, so (per the Home-carousel gotcha) we don't click a card by
 * name — we tap the first card by position and assert it opens *a* GameDetail.
 */
class RecentlyAddedModuleTest {

    @Test
    fun showsRecentlyAddedSection() = runChipboxUiTest {
        assertSectionHeader("Recently added")
    }

    @Test
    fun tappingRecentlyAddedGameOpensDetail() = runChipboxUiTest {
        // Scroll the row into view first, then tap its first (reliably on-screen) card.
        assertSectionHeader("Recently added")
        clickFirstCardInHomeSection("Recently added")
        assertNavigationEventOfType<GameDetail>()
    }
}
