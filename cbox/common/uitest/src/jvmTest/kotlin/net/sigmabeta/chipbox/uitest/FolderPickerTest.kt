package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.folderpicker.FolderPicker
import kotlin.test.Test

/**
 * The in-app folder picker renders its "add this folder" / "cancel" controls.
 *
 * (Its actions aren't asserted in isolation: "Add this folder" starts the scanner, which touches
 * `Dispatchers.Main` outside the harness's controlled dispatcher; "Cancel" emits a `NavigateBack`
 * that the harness doesn't record as a destination; and the fake filesystem has no subfolders to
 * descend into.)
 */
class FolderPickerTest {
    @Test
    fun showsPickerControls() = runChipboxUiTest {
        startAtScreen(FolderPicker)

        assertDisplayed("Add this folder")
        assertDisplayed("Cancel and exit")
    }
}
