package net.sigmabeta.chipbox.features.folderpicker

import net.sigmabeta.chipbox.appcomm.ChipboxAction

sealed class FolderPickerAction : ChipboxAction() {
    data object AddThisFolderClicked : FolderPickerAction()
    data object CancelClicked : FolderPickerAction()
    data class FolderClicked(val path: String) : FolderPickerAction()
}
