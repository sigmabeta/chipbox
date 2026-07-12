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
    storageVolumeProvider: StorageVolumeProvider,
    private val librarySource: LibrarySource,
    private val scanner: Scanner,
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxListViewModel<FolderPickerState>(
    // Seed the volume list once — it's constant for the screen's lifetime, and copy() carries it
    // through every subsequent state update. The provider decides per platform (Android via
    // StorageManager, empty on desktop) so this screen stays free of platform filesystem logic.
    FolderPickerState(volumes = storageVolumeProvider.volumes()),
    stringProvider,
    hatchet,
) {
    private val volumes = state.value.volumes

    // Paths that ARE a volume root, for deciding when "up" opens the volume chooser instead of the
    // (unreadable-on-Android) filesystem parent above the root.
    private val volumeRoots = volumes.map { it.path }.toSet()

    init {
        // Land on the volume chooser when there's a genuine choice (more than one mounted volume),
        // so an SD card is one tap away rather than buried behind "up". With a single volume there's
        // nothing to choose, so drop straight into [defaultPath] — the per-OS landing directory from
        // the platform Route actual (~ on Unix, getExternalStorageDirectory() on Android). Listing
        // runs in the launch because java.io.File walks block; it unblocks composition.
        viewModelScope.launch {
            if (volumes.size > 1) {
                showVolumeList()
            } else {
                descendInto(defaultPath)
            }
        }
    }

    override fun handleAction(action: SageAction) {
        when (action) {
            FolderPickerAction.AddThisFolderClicked -> onAddClicked()

            FolderPickerAction.CancelClicked -> emit(ChipboxEvent.NavigateBack)

            FolderPickerAction.NavigateUpClicked -> viewModelScope.launch {
                val current = state.value
                when {
                    // At a volume root with more than one volume, "up" is the volume chooser.
                    current.parentIsVolumeList -> showVolumeList()

                    // Otherwise ascend to the filesystem parent; no-op at a root (null parent).
                    else -> current.parentPath?.let { descendInto(it) }
                }
            }

            FolderPickerAction.ReturnToDefaultClicked -> viewModelScope.launch {
                // Reliable escape from an unreadable directory: jump straight back to the per-OS
                // landing folder ([defaultPath]) rather than ascending through more traverse-only
                // parents that are themselves unreadable (Android's /storage/emulated, /storage, /).
                descendInto(defaultPath)
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
        val atVolumeRoot = path in volumeRoots
        updateState {
            it.copy(
                currentPath = path,
                entries = listing.folders,
                fileCount = listing.fileCount,
                // At a volume root, don't expose the filesystem parent (Android's traverse-only,
                // unreadable /storage chain). Instead, when there's more than one volume, "up"
                // opens the chooser; with a single volume the root is simply the top.
                parentPath = if (atVolumeRoot) null else listing.parentPath,
                parentIsVolumeList = atVolumeRoot && volumes.size > 1,
                showHidden = showHidden,
                readable = listing.readable,
                atVolumeList = false,
            )
        }
    }

    // Render the synthetic volume chooser: no current directory, no filesystem parent — just the
    // volume rows and the cancel escape.
    private fun showVolumeList() {
        updateState {
            it.copy(
                currentPath = null,
                entries = emptyList(),
                fileCount = 0,
                parentPath = null,
                parentIsVolumeList = false,
                readable = true,
                atVolumeList = true,
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
