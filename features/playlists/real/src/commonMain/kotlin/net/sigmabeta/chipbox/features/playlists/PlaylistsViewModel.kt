package net.sigmabeta.chipbox.features.playlists

import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.appcomm.ChipboxEvent.NavigateBack
import net.sigmabeta.chipbox.appcomm.ChipboxEvent.NavigateTo
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListViewModel
import net.sigmabeta.chipbox.features.playlistdetail.PlaylistDetail
import net.sigmabeta.chipbox.playlists.PlaylistsRepository
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

// `pendingTrackIds` is the only route arg, taken as `@Assisted` and passed by the pushed Screen.
// Empty = normal browse list; non-empty = "Add to Playlist" picker mode.
@AssistedInject
class PlaylistsViewModel(
    @Assisted private val pendingTrackIds: List<Long>,
    private val playlists: PlaylistsRepository,
    private val stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxListViewModel<PlaylistsState>(
    PlaylistsState(isPicker = pendingTrackIds.isNotEmpty()),
    stringProvider,
    hatchet,
) {

    init {
        viewModelScope.launch {
            playlists.playlists().collect { list ->
                updateState { it.copy(playlists = LCE.Content(list)) }
            }
        }
    }

    override fun handleAction(action: SageAction) {
        when (action) {
            is PlaylistsAction.PlaylistClicked -> onPlaylistClicked(action.id)
            PlaylistsAction.NewPlaylistClicked -> createPlaylist()
            else -> Unit
        }
    }

    private fun onPlaylistClicked(id: Long) {
        if (pendingTrackIds.isEmpty()) {
            // Browse mode: open the playlist.
            emit(NavigateTo(PlaylistDetail(id)))
        } else {
            // Picker mode: drop the tracks into the chosen playlist and return to where we came from.
            viewModelScope.launch {
                playlists.addTracks(id, pendingTrackIds)
                emit(NavigateBack)
            }
        }
    }

    private fun createPlaylist() {
        // Create an empty playlist (seeding the picker's pending tracks, if any), then open its detail.
        // (Edit mode / inline naming arrives in a later slice; for now it starts with a default name.)
        viewModelScope.launch {
            val name = stringProvider.getString(ChipboxStringId.PLAYLISTS_DEFAULT_NAME)
            val id = playlists.createPlaylist(name)
            if (pendingTrackIds.isNotEmpty()) {
                playlists.addTracks(id, pendingTrackIds)
            }
            emit(NavigateTo(PlaylistDetail(id)))
        }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(@Assisted pendingTrackIds: List<Long>): PlaylistsViewModel
    }
}
