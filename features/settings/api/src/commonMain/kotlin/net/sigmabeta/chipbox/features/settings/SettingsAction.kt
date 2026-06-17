package net.sigmabeta.chipbox.features.settings

import net.sigmabeta.chipbox.appcomm.ChipboxAction
import net.sigmabeta.chipbox.debug.GeneratorSource
import net.sigmabeta.chipbox.debug.ImageLoaderSource
import net.sigmabeta.chipbox.debug.RepositorySource
import net.sigmabeta.chipbox.debug.SpeakerSource
import net.sigmabeta.chipbox.settings.ResamplerMode
import net.sigmabeta.chipbox.settings.ThemeMode
import net.sigmabeta.chipbox.ui.fonts.ChipboxFont

sealed class SettingsAction : ChipboxAction() {
    data class DropdownExpandClicked(val settingId: String) : SettingsAction()
    data class ThemeModeSelected(val mode: ThemeMode) : SettingsAction()
    data class ResamplerModeSelected(val mode: ResamplerMode) : SettingsAction()
    data object ShuffleSkipsShortTracksToggled : SettingsAction()
    data class RepositorySourceSelected(val source: RepositorySource) : SettingsAction()
    data class GeneratorSourceSelected(val source: GeneratorSource) : SettingsAction()
    data class SpeakerSourceSelected(val source: SpeakerSource) : SettingsAction()
    data class ImageLoaderSourceSelected(val source: ImageLoaderSource) : SettingsAction()
    data class BrandFontSelected(val font: ChipboxFont) : SettingsAction()
    data class PlainFontSelected(val font: ChipboxFont) : SettingsAction()
    data object AddFolderClicked : SettingsAction()
    data object ManageLibraryClicked : SettingsAction()
    data class FolderPicked(val uri: String) : SettingsAction()
    data object RescanLibraryClicked : SettingsAction()
    data object RescanStatusClicked : SettingsAction()
    data object ClearLibraryClicked : SettingsAction()
    data object ClearPlaybackHistoryClicked : SettingsAction()
    data object LicensesClicked : SettingsAction()
    data object GithubClicked : SettingsAction()
    data object BuildDateClicked : SettingsAction()
    data object PlaybackStatusClicked : SettingsAction()
    data object ErrorLogClicked : SettingsAction()
    data object CrashLogClicked : SettingsAction()
    data object ComponentLibraryClicked : SettingsAction()
}
