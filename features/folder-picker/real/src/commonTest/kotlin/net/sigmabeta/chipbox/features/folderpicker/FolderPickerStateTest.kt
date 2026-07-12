package net.sigmabeta.chipbox.features.folderpicker

import net.sigmabeta.sage.ui.SageStringId
import net.sigmabeta.sage.ui.StringProvider
import kotlin.test.Test
import kotlin.test.assertTrue

class FolderPickerStateTest {

    private val volumes = listOf(
        StorageVolumeInfo("Internal shared storage", "/storage/emulated/0"),
        StorageVolumeInfo("SD card", "/storage/1A2B-3C4D"),
    )

    /**
     * Regression guard: the volume chooser must share no LazyColumn item key ([ListModel.dataId])
     * with the folder browser. It's kept CTA-free (exit is the app-bar back arrow) precisely so this
     * holds — a key shared between the two would survive the chooser→folder swap as a scroll anchor,
     * leaving the prepended CTAs (Add / Go up / hidden-toggle) above the viewport until the next
     * navigation. Re-adding a shared row (e.g. "Cancel and exit") to the chooser would fail this.
     */
    @Test
    fun `volume chooser shares no list-item key with the folder browser`() {
        val chooserIds = FolderPickerState(atVolumeList = true, volumes = volumes)
            .toListItems(stubStringProvider())
            .map { it.dataId }
            .toSet()
        val folderIds = FolderPickerState(
            currentPath = "/storage/emulated/0",
            entries = listOf(FolderPickerEntry("Music", "/storage/emulated/0/Music", 0, 3)),
            parentIsVolumeList = true,
            volumes = volumes,
        )
            .toListItems(stubStringProvider())
            .map { it.dataId }
            .toSet()

        assertTrue(
            chooserIds.intersect(folderIds).isEmpty(),
            "Shared keys let prepended CTAs anchor off-screen: ${chooserIds.intersect(folderIds)}",
        )
    }

    private fun stubStringProvider() = object : StringProvider {
        override fun getString(string: SageStringId): String = string.toString()
        override fun getStringOneArg(string: SageStringId, arg: String): String = string.toString()
        override fun getStringOneInt(string: SageStringId, arg: Int): String = string.toString()
        override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String =
            string.toString()
    }
}
