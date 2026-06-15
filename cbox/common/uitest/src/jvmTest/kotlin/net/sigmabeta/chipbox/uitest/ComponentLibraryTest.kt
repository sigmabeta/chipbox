package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.componentlibrary.ComponentLibrary
import net.sigmabeta.chipbox.features.componentlibrary.ComponentLibraryMode
import net.sigmabeta.chipbox.features.componentlibrary.LibraryMode
import kotlin.test.Test

/** The debug component gallery menu — three rows, each opening that gallery mode. */
class ComponentLibraryTest {
    @Test
    fun showsModeMenu() = runChipboxUiTest {
        startAtScreen(ComponentLibrary)

        assertDisplayed("List")
        assertDisplayed("Grid")
        assertDisplayed("Columns")
    }

    @Test
    fun listRowNavigates() = runChipboxUiTest {
        startAtScreen(ComponentLibrary)

        click("List")

        assertNavigationEvent(ComponentLibraryMode(LibraryMode.LIST))
    }

    @Test
    fun gridRowNavigates() = runChipboxUiTest {
        startAtScreen(ComponentLibrary)

        click("Grid")

        assertNavigationEvent(ComponentLibraryMode(LibraryMode.GRID))
    }

    @Test
    fun columnsRowNavigates() = runChipboxUiTest {
        startAtScreen(ComponentLibrary)

        click("Columns")

        assertNavigationEvent(ComponentLibraryMode(LibraryMode.COLUMNS))
    }
}
