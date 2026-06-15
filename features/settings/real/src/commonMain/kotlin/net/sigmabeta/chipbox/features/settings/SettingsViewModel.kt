package net.sigmabeta.chipbox.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.contentsource.LibrarySource
import net.sigmabeta.chipbox.debug.DebugSettingsManager
import net.sigmabeta.chipbox.features.componentlibrary.ComponentLibrary
import net.sigmabeta.chipbox.features.crashlog.CrashLog
import net.sigmabeta.chipbox.features.errorlog.ErrorLog
import net.sigmabeta.chipbox.features.managelibrary.ManageLibrary
import net.sigmabeta.chipbox.features.playbackstatus.PlaybackStatus
import net.sigmabeta.chipbox.features.rescanstatus.RescanStatus
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.scanner.Scanner
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.chipbox.settings.ChipboxSettingsManager
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListViewModel
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.appinfo.AppInfo
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class SettingsViewModel @Inject constructor(
    private val settingsManager: ChipboxSettingsManager,
    private val debugSettingsManager: DebugSettingsManager,
    private val repository: Repository,
    private val scanner: Scanner,
    private val librarySource: LibrarySource,
    private val appInfo: AppInfo,
    stringProvider: StringProvider,
    private val hatchet: Hatchet,
) : ChipboxListViewModel<SettingsState>(
    SettingsState(),
    stringProvider,
    hatchet,
) {
    init {
        updateState {
            it.copy(
                appInfo = appInfo,
                formattedBuildDate = formatBuildDate(appInfo.buildTimeMs),
            )
        }

        viewModelScope.launch {
            settingsManager.getBrandFont().collect { name ->
                updateState { it.copy(brandFont = name) }
            }
        }
        viewModelScope.launch {
            settingsManager.getPlainFont().collect { name ->
                updateState { it.copy(plainFont = name) }
            }
        }
        viewModelScope.launch {
            settingsManager.getThemeMode().collect { mode ->
                updateState { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            settingsManager.getResamplerMode().collect { mode ->
                updateState { it.copy(resamplerMode = mode) }
            }
        }
        viewModelScope.launch {
            debugSettingsManager.getShouldShowDebug().collect { value ->
                updateState { it.copy(shouldShowDebug = value) }
            }
        }
        viewModelScope.launch {
            debugSettingsManager.getRepositorySource().collect { source ->
                updateState { it.copy(repositorySource = source) }
            }
        }
        viewModelScope.launch {
            debugSettingsManager.getGeneratorSource().collect { source ->
                updateState { it.copy(generatorSource = source) }
            }
        }
        viewModelScope.launch {
            debugSettingsManager.getSpeakerSource().collect { source ->
                updateState { it.copy(speakerSource = source) }
            }
        }
        viewModelScope.launch {
            debugSettingsManager.getImageLoaderSource().collect { source ->
                updateState { it.copy(imageLoaderSource = source) }
            }
        }
        viewModelScope.launch {
            librarySource.locations.collect { locations ->
                updateState { it.copy(hasLibraryFolders = locations.isNotEmpty()) }
            }
        }
        viewModelScope.launch {
            scanner.state().collect { scannerState ->
                updateState { current ->
                    val isScanning = scannerState is ScannerState.Scanning
                    val nextStatus = when {
                        isScanning -> LCE.Loading(LOAD_OP_RESCAN)
                        current.rescanStatus is LCE.Loading -> LCE.Uninitialized
                        else -> current.rescanStatus
                    }
                    current.copy(rescanStatus = nextStatus)
                }
            }
        }
    }

    override fun handleAction(action: SageAction) {
        when (action) {
            is SettingsAction.DropdownExpandClicked -> updateState {
                // At most one dropdown open at a time: re-tapping the open one closes it; tapping
                // any other replaces it as the single expanded dropdown.
                val next = if (it.expandedDropdownId == action.settingId) null else action.settingId
                it.copy(expandedDropdownId = next)
            }

            is SettingsAction.ThemeModeSelected -> {
                settingsManager.setThemeMode(action.mode)
                collapseDropdowns()
            }

            is SettingsAction.ResamplerModeSelected -> {
                settingsManager.setResamplerMode(action.mode)
                collapseDropdowns()
            }

            is SettingsAction.RepositorySourceSelected -> {
                debugSettingsManager.setRepositorySource(action.source)
                collapseDropdowns()
            }

            is SettingsAction.GeneratorSourceSelected -> {
                debugSettingsManager.setGeneratorSource(action.source)
                collapseDropdowns()
            }

            is SettingsAction.SpeakerSourceSelected -> {
                debugSettingsManager.setSpeakerSource(action.source)
                collapseDropdowns()
            }

            is SettingsAction.ImageLoaderSourceSelected -> {
                debugSettingsManager.setImageLoaderSource(action.source)
                collapseDropdowns()
            }

            is SettingsAction.BrandFontSelected -> {
                settingsManager.setBrandFont(action.font.name)
                collapseDropdowns()
            }

            is SettingsAction.PlainFontSelected -> {
                settingsManager.setPlainFont(action.font.name)
                collapseDropdowns()
            }

            SettingsAction.AddFolderClicked -> emit(ChipboxEvent.PickFolder)

            SettingsAction.ManageLibraryClicked -> emit(ChipboxEvent.NavigateTo(ManageLibrary))

            is SettingsAction.FolderPicked -> onFolderPicked(action.uri)

            SettingsAction.RescanLibraryClicked -> onRescanClicked()

            SettingsAction.RescanStatusClicked -> emit(ChipboxEvent.NavigateTo(RescanStatus))

            SettingsAction.ClearLibraryClicked -> onClearLibraryClicked()

            SettingsAction.LicensesClicked -> emit(
                ChipboxEvent.ShowSnackbar("Licenses screen coming soon.")
            )

            SettingsAction.GithubClicked -> emit(ChipboxEvent.OpenUrl(GITHUB_URL))

            SettingsAction.BuildDateClicked -> onBuildDateClicked()

            SettingsAction.PlaybackStatusClicked -> emit(ChipboxEvent.NavigateTo(PlaybackStatus))

            SettingsAction.ErrorLogClicked -> emit(ChipboxEvent.NavigateTo(ErrorLog))

            SettingsAction.CrashLogClicked -> emit(ChipboxEvent.NavigateTo(CrashLog))

            SettingsAction.ComponentLibraryClicked -> emit(ChipboxEvent.NavigateTo(ComponentLibrary))

            else -> Unit
        }
    }

    // Picking an option closes the dropdown it came from; selection lives across the four
    // *Selected actions, so collapse in one place.
    private fun collapseDropdowns() = updateState { it.copy(expandedDropdownId = null) }

    private fun onFolderPicked(uri: String) {
        librarySource.addLibraryLocation(uri)
        // Adding a folder kicks off a scan and opens the live status screen (via onRescanClicked).
        onRescanClicked()
    }

    private fun onRescanClicked() {
        updateState { it.copy(rescanStatus = LCE.Loading(LOAD_OP_RESCAN)) }
        scanner.startScan()
        emit(ChipboxEvent.NavigateTo(RescanStatus))
    }

    @Suppress("TooGenericExceptionCaught")
    private fun onClearLibraryClicked() {
        updateState { it.copy(clearLibraryStatus = LCE.Loading(LOAD_OP_CLEAR)) }
        viewModelScope.launch {
            try {
                repository.clearLibrary()
                updateState { it.copy(clearLibraryStatus = LCE.Content(Unit)) }
                emit(ChipboxEvent.ShowSnackbar("Library cleared."))
            } catch (ex: Throwable) {
                hatchet.e("Clear library failed: ${ex.message}")
                updateState { it.copy(clearLibraryStatus = LCE.Error(LOAD_OP_CLEAR, ex)) }
                emit(ChipboxEvent.ShowSnackbar("Failed to clear library."))
            }
        }
    }

    private fun onBuildDateClicked() {
        val current = state.value
        val next = current.debugClickCount + 1
        if (next >= DEBUG_TAP_THRESHOLD) {
            val newShouldShow = !(current.shouldShowDebug ?: false)
            debugSettingsManager.setShouldShowDebug(newShouldShow)
            updateState { it.copy(debugClickCount = 0) }
        } else {
            updateState { it.copy(debugClickCount = next) }
        }
    }

    private fun formatBuildDate(epochMs: Long?): String? =
        epochMs?.let { formatLongDate(it) }

    private companion object {
        private const val LOAD_OP_RESCAN = "settings.rescan"
        private const val LOAD_OP_CLEAR = "settings.clear_library"
        private const val DEBUG_TAP_THRESHOLD = 5
        private const val GITHUB_URL = "https://github.com/sigmabeta/chipbox"
    }
}
