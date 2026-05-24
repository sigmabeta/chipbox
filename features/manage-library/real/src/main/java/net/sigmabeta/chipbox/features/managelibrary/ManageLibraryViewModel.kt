package net.sigmabeta.chipbox.features.managelibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListViewModel
import net.sigmabeta.chipbox.contentsource.LibrarySource
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class ManageLibraryViewModel @Inject constructor(
    private val librarySource: LibrarySource,
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxListViewModel<ManageLibraryState>(
    ManageLibraryState(),
    stringProvider,
    hatchet,
) {
    init {
        // The folder list is whatever LibrarySource currently holds; add/remove below mutate that
        // StateFlow, so the list re-renders without us touching state here.
        viewModelScope.launch {
            librarySource.locations.collect { locations ->
                updateState { state ->
                    state.copy(
                        folders = locations.map { LibraryFolder(it.identifier, it.displayName) },
                    )
                }
            }
        }
    }

    override fun handleAction(action: SageAction) {
        when (action) {
            ManageLibraryAction.AddFolderClicked -> emit(ChipboxEvent.PickFolder)
            is ManageLibraryAction.FolderPicked -> onFolderPicked(action.uri)
            is ManageLibraryAction.FolderClicked -> onFolderRemoved(action.identifier)
            else -> Unit
        }
    }

    private fun onFolderPicked(uri: String) {
        librarySource.addLibraryLocation(uri)
        emit(ChipboxEvent.ShowSnackbar("Folder added to library."))
    }

    private fun onFolderRemoved(identifier: String) {
        librarySource.removeLibraryLocation(identifier)
        emit(ChipboxEvent.ShowSnackbar("Folder removed from library."))
    }
}
