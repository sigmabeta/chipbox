package net.sigmabeta.chipbox.features.folderpicker

import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListViewModel
import net.sigmabeta.chipbox.contentsource.LibrarySource
import net.sigmabeta.chipbox.features.rescanstatus.RescanStatus
import net.sigmabeta.chipbox.scanner.Scanner
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

@AssistedInject
class FolderPickerViewModel(
    @Assisted private val defaultPath: String,
    private val folderLister: FolderLister,
    private val librarySource: LibrarySource,
    private val scanner: Scanner,
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxListViewModel<FolderPickerState>(
    FolderPickerState(),
    stringProvider,
    hatchet,
) {
    init {
        // [defaultPath] is the per-OS landing directory passed in from the platform Route actual
        // (~ on Unix, getExternalStorageDirectory() on Android). Listing runs on the disk
        // dispatcher because java.io.File walks block; the launch unblocks composition.
        viewModelScope.launch {
            descendInto(defaultPath)
        }
    }

    override fun handleAction(action: SageAction) {
        when (action) {
            FolderPickerAction.AddThisFolderClicked -> onAddClicked()

            FolderPickerAction.CancelClicked -> emit(ChipboxEvent.NavigateBack)

            FolderPickerAction.NavigateUpClicked -> viewModelScope.launch {
                // No-op at a filesystem root, where the lister reports a null parent.
                state.value.parentPath?.let { descendInto(it) }
            }

            FolderPickerAction.ToggleHiddenClicked -> viewModelScope.launch {
                // Flip the dotfile filter and re-list the current directory in place.
                val path = state.value.currentPath ?: return@launch
                descendInto(path, showHidden = !state.value.showHidden)
            }

            is FolderPickerAction.FolderClicked -> viewModelScope.launch {
                descendInto(action.path)
            }

            else -> Unit
        }
    }

    private fun onAddClicked() {
        val path = state.value.currentPath ?: return
        librarySource.addLibraryLocation(path)
        // Mirrors ManageLibraryViewModel.onFolderPicked: adding a folder kicks off a scan and
        // hands the user off to the live scan-status screen.
        scanner.startScan()
        emit(ChipboxEvent.NavigateTo(RescanStatus))
    }

    // [showHidden] carries forward from the current state by default so descending into a folder
    // keeps the user's dotfile preference; the toggle handler passes the flipped value explicitly.
    private fun descendInto(path: String, showHidden: Boolean = state.value.showHidden) {
        val listing = folderLister.list(path, showHidden)
        updateState {
            it.copy(
                currentPath = path,
                entries = listing.folders,
                fileCount = listing.fileCount,
                parentPath = listing.parentPath,
                showHidden = showHidden,
            )
        }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(@Assisted defaultPath: String): FolderPickerViewModel
    }
}
