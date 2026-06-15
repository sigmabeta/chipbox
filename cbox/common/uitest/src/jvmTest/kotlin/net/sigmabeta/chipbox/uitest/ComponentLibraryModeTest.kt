package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.componentlibrary.ComponentLibraryMode
import net.sigmabeta.chipbox.features.componentlibrary.LibraryMode
import kotlin.test.Test

/** A component-gallery mode sub-screen renders the sample components under its mode title. Pure
 *  display — the only interactive element is a sample dropdown. */
class ComponentLibraryModeTest {
    @Test
    fun listModeShowsItsTitle() = runChipboxUiTest {
        startAtScreen(ComponentLibraryMode(LibraryMode.LIST))

        assertTitle("List")
    }
}
