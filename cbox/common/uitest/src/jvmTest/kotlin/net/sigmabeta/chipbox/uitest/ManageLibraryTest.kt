package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.managelibrary.ManageLibrary
import kotlin.test.Test

/**
 * Manage-library with no folders configured (the fake library source is empty) shows the empty state
 * and the "add folder" CTA. (Adding/removing folders emits a folder-picker event handled outside the
 * harness, so only the empty content is asserted here.)
 */
class ManageLibraryTest {
    @Test
    fun showsEmptyStateAndAddCta() = runChipboxUiTest {
        startAtScreen(ManageLibrary)

        assertEmptyStateDisplayed("No folders in your library. Add one to get started.")
        assertCtaDisplayed("Add folder to library")
    }
}
