package net.sigmabeta.chipbox.features.library

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import net.sigmabeta.chipbox.appcomm.ChipboxEvent.NavigateTo
import net.sigmabeta.chipbox.features.browsealltracks.BrowseAllTracks
import net.sigmabeta.chipbox.features.browsebyartist.BrowseByArtist
import net.sigmabeta.chipbox.features.browsebygame.BrowseByGame
import net.sigmabeta.chipbox.features.browsebyplatform.BrowseByPlatform
import net.sigmabeta.chipbox.features.favorites.Favorites
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListViewModel
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class LibraryViewModel @Inject constructor(
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxListViewModel<LibraryState>(LibraryState, stringProvider, hatchet) {

    override fun handleAction(action: SageAction) {
        when (action) {
            LibraryAction.FavoritesClicked -> emit(NavigateTo(Favorites))
            LibraryAction.BrowseByGameClicked -> emit(NavigateTo(BrowseByGame))
            LibraryAction.BrowseByPlatformClicked -> emit(NavigateTo(BrowseByPlatform))
            LibraryAction.BrowseByArtistClicked -> emit(NavigateTo(BrowseByArtist))
            LibraryAction.BrowseAllTracksClicked -> emit(NavigateTo(BrowseAllTracks))
            else -> Unit
        }
    }
}
