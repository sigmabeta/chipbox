package net.sigmabeta.chipbox.features.settings

import net.sigmabeta.chipbox.appcomm.ChipboxAction

sealed class SettingsAction : ChipboxAction() {
    data object AddFolderClicked : SettingsAction()
    data class FolderPicked(val uri: String) : SettingsAction()
    data object RescanLibraryClicked : SettingsAction()
    data object ClearLibraryClicked : SettingsAction()
    data object LicensesClicked : SettingsAction()
    data object GithubClicked : SettingsAction()
    data object BuildDateClicked : SettingsAction()
}
