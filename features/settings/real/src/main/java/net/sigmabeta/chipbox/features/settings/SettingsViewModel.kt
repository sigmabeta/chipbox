package net.sigmabeta.chipbox.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.contentsource.LibrarySource
import net.sigmabeta.chipbox.debug.DebugSettingsManager
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.scanner.Scanner
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.chipbox.settings.ChipboxSettingsManager
import net.sigmabeta.chipbox.ui.list.ChipboxListViewModel
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
            debugSettingsManager.getShouldShowDebug().collect { value ->
                updateState { it.copy(shouldShowDebug = value) }
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
            SettingsAction.AddFolderClicked -> emit(ChipboxEvent.PickFolder)

            is SettingsAction.FolderPicked -> onFolderPicked(action.uri)

            SettingsAction.RescanLibraryClicked -> onRescanClicked()

            SettingsAction.ClearLibraryClicked -> onClearLibraryClicked()

            SettingsAction.LicensesClicked -> emit(
                ChipboxEvent.ShowSnackbar("Licenses screen coming soon.")
            )

            SettingsAction.GithubClicked -> emit(ChipboxEvent.OpenUrl(GITHUB_URL))

            SettingsAction.BuildDateClicked -> onBuildDateClicked()

            SettingsAction.PlaybackStatusClicked -> {
                hatchet.w(
                    "Playback Status screen not migrated yet — re-link when the feature lands.",
                )
                emit(ChipboxEvent.ShowSnackbar("Playback Status: not implemented yet."))
            }

            else -> Unit
        }
    }

    private fun onFolderPicked(uri: String) {
        librarySource.addLibraryLocation(uri)
        emit(ChipboxEvent.ShowSnackbar("Folder added to library."))
    }

    private fun onRescanClicked() {
        updateState { it.copy(rescanStatus = LCE.Loading(LOAD_OP_RESCAN)) }
        scanner.startScan()
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

    private fun formatBuildDate(epochMs: Long?): String? {
        if (epochMs == null) return null
        return Instant.ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
            .format(BUILD_DATE_FORMATTER)
    }

    private companion object {
        private const val LOAD_OP_RESCAN = "settings.rescan"
        private const val LOAD_OP_CLEAR = "settings.clear_library"
        private const val DEBUG_TAP_THRESHOLD = 5
        private const val GITHUB_URL = "https://github.com/sigmabeta/chipbox"

        private val BUILD_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter
            .ofLocalizedDate(FormatStyle.LONG)
    }
}
