package net.sigmabeta.chipbox.features.playlistdetail

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
import net.sigmabeta.chipbox.playlists.PlaylistsRepository
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

// `playlistId` is the only route arg, taken as `@Assisted` and passed by the pushed Screen — see
// the GameDetailViewModel note for why Voyager + Metro wire it this way (no SavedStateHandle).
@AssistedInject
class PlaylistDetailViewModel(
    @Assisted private val playlistId: Long,
    private val repository: Repository,
    private val playlists: PlaylistsRepository,
    private val stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxListViewModel<PlaylistDetailState>(
    PlaylistDetailState(),
    stringProvider,
    hatchet,
) {

    init {
        // A null playlist means it was deleted (or never existed) — surface the not-found state.
        viewModelScope.launch {
            playlists.playlist(playlistId).collect { playlist ->
                updateState {
                    if (playlist == null) {
                        it.copy(playlist = LCE.Uninitialized, notFound = true)
                    } else {
                        it.copy(playlist = LCE.Content(playlist), notFound = false)
                    }
                }
            }
        }

        // Hydrate the membership ids into full tracks via the library repository, preserving order.
        viewModelScope.launch {
            playlists.trackIds(playlistId).collect { ids ->
                updateState { it.copy(tracks = LCE.Loading(LOAD_OP)) }
                val tracks = ids.mapNotNull { repository.getTrack(it, withGame = true, withArtists = true) }
                updateState { it.copy(tracks = LCE.Content(tracks)) }
            }
        }
    }

    override fun handleAction(action: SageAction) {
        when (action) {
            PlaylistDetailAction.EditClicked -> updateState { it.copy(isEditing = true) }

            PlaylistDetailAction.DoneClicked -> updateState { it.copy(isEditing = false) }

            PlaylistDetailAction.RenameClicked ->
                emit(ChipboxEvent.ShowSnackbar(stringProvider.getString(ChipboxStringId.PLAYLIST_DETAIL_RENAME_COMING_SOON)))

            PlaylistDetailAction.DeleteClicked -> deletePlaylist()

            is PlaylistDetailAction.TrackRemoved -> removeTrack(action.trackId)

            is SageAction.Reorder -> reorderTracks(action.fromIndex, action.toIndex)

            else -> Unit
        }
    }

    private fun deletePlaylist() {
        viewModelScope.launch {
            playlists.deletePlaylist(playlistId)
            emit(ChipboxEvent.ShowSnackbar(stringProvider.getString(ChipboxStringId.PLAYLIST_DETAIL_DELETED)))
            emit(ChipboxEvent.NavigateBack)
        }
    }

    private fun removeTrack(trackId: Long) {
        val current = currentTrackIds() ?: return
        viewModelScope.launch { playlists.setTrackOrder(playlistId, current.filterNot { it == trackId }) }
    }

    /**
     * Apply a completed drag. [fromIndex]/[toIndex] are positions in the rendered list, which in
     * edit mode is [PLAYLIST_EDIT_HEADER_ROWS] fixed CTA rows followed by the track rows — so shift
     * both into track space and clamp (a track dropped onto/above the CTAs lands at the ends).
     */
    private fun reorderTracks(fromIndex: Int, toIndex: Int) {
        val current = currentTrackIds() ?: return
        if (current.isEmpty()) return
        val from = (fromIndex - PLAYLIST_EDIT_HEADER_ROWS).coerceIn(0, current.lastIndex)
        val to = (toIndex - PLAYLIST_EDIT_HEADER_ROWS).coerceIn(0, current.lastIndex)
        if (from == to) return
        val reordered = current.toMutableList().apply { add(to, removeAt(from)) }
        viewModelScope.launch { playlists.setTrackOrder(playlistId, reordered) }
    }

    private fun currentTrackIds(): List<Long>? = (state.value.tracks as? LCE.Content)?.data?.map { it.id }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(@Assisted playlistId: Long): PlaylistDetailViewModel
    }

    private companion object {
        const val LOAD_OP = "playlist_detail.load"
    }
}
