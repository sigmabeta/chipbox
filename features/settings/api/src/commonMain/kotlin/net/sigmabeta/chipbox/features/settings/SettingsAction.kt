package net.sigmabeta.chipbox.features.settings

import net.sigmabeta.chipbox.appcomm.ChipboxAction
import net.sigmabeta.chipbox.settings.ThemeMode
import net.sigmabeta.chipbox.ui.fonts.ChipboxFont

sealed class SettingsAction : ChipboxAction() {
    data class ThemeModeSelected(val mode: ThemeMode) : SettingsAction()
    data class BrandFontSelected(val font: ChipboxFont) : SettingsAction()
    data class PlainFontSelected(val font: ChipboxFont) : SettingsAction()
    data object AddFolderClicked : SettingsAction()
    data object ManageLibraryClicked : SettingsAction()
    data class FolderPicked(val uri: String) : SettingsAction()
    data object RescanLibraryClicked : SettingsAction()
    data object RescanStatusClicked : SettingsAction()
    data object ClearLibraryClicked : SettingsAction()
    data object LicensesClicked : SettingsAction()
    data object GithubClicked : SettingsAction()
    data object BuildDateClicked : SettingsAction()
    data object PlaybackStatusClicked : SettingsAction()
    data object ErrorLogClicked : SettingsAction()
    data object ComponentLibraryClicked : SettingsAction()
}
