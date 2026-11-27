package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.componentlibrary.ComponentLibrary
import kotlin.test.Test

/** The debug component gallery: one screen, SAGE's catalog plus Chipbox's own cards. */
class ComponentLibraryTest {
    @Test
    fun showsItsTitle() = runChipboxUiTest {
        startAtScreen(ComponentLibrary)

        assertTitle("Component library")
    }

    @Test
    fun showsTheLayoutControls() = runChipboxUiTest {
        startAtScreen(ComponentLibrary)

        assertSectionHeader("Controls")
        assertDisplayed("Layout: List")
    }
}
