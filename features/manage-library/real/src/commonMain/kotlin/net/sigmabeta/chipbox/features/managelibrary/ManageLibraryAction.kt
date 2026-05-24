package net.sigmabeta.chipbox.features.managelibrary

import net.sigmabeta.chipbox.appcomm.ChipboxAction

sealed class ManageLibraryAction : ChipboxAction() {
    data object AddFolderClicked : ManageLibraryAction()
    data class FolderPicked(val uri: String) : ManageLibraryAction()
    data class FolderClicked(val identifier: String) : ManageLibraryAction()
}
