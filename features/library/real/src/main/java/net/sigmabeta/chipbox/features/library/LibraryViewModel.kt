package net.sigmabeta.chipbox.features.library

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import net.sigmabeta.chipbox.appcomm.ChipboxNavEvent.NavigateTo
import net.sigmabeta.chipbox.features.browsealltracks.BrowseAllTracks
import net.sigmabeta.chipbox.features.browsebyartist.BrowseByArtist
import net.sigmabeta.chipbox.features.browsebygame.BrowseByGame
import net.sigmabeta.chipbox.ui.list.ChipboxListViewModel
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

@HiltViewModel
class LibraryViewModel @Inject constructor(
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxListViewModel<LibraryState>(LibraryState, stringProvider, hatchet) {

    override fun handleAction(action: SageAction) {
        when (action) {
            LibraryAction.BrowseByGameClicked -> emit(NavigateTo(BrowseByGame))
            LibraryAction.BrowseByArtistClicked -> emit(NavigateTo(BrowseByArtist))
            LibraryAction.BrowseAllTracksClicked -> emit(NavigateTo(BrowseAllTracks))
            else -> Unit
        }
    }
}
