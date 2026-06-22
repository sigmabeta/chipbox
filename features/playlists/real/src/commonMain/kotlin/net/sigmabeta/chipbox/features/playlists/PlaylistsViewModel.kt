package net.sigmabeta.chipbox.features.playlists

import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.flow.first
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

// Route args, taken as `@Assisted` and passed by the pushed Screen: [pendingTrackIds] empty = normal
// browse list, non-empty = "Add to Playlist" picker mode; [suggestedName] seeds a created playlist's
// name (null in browse mode).
@AssistedInject
class PlaylistsViewModel(
    @Assisted private val pendingTrackIds: List<Long>,
    @Assisted private val suggestedName: String?,
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
        // Use the caller's suggested name (e.g. "From game …") when present, else the generic default.
        viewModelScope.launch {
            val base = suggestedName?.takeIf { it.isNotBlank() }
                ?: stringProvider.getString(ChipboxStringId.PLAYLISTS_DEFAULT_NAME)
            val existingNames = playlists.playlists().first().mapTo(mutableSetOf()) { it.name }
            val id = playlists.createPlaylist(uniqueDefaultName(base, existingNames))
            if (pendingTrackIds.isNotEmpty()) {
                playlists.addTracks(id, pendingTrackIds)
            }
            emit(NavigateTo(PlaylistDetail(id)))
        }
    }

    // Playlist names must be unique, so a fresh playlist appends the lowest free integer to the
    // base when it (or a numbered sibling) is already taken: "New Playlist", "New Playlist 2"…
    private fun uniqueDefaultName(base: String, existingNames: Set<String>): String {
        if (base !in existingNames) return base
        var suffix = 2
        while ("$base $suffix" in existingNames) suffix++
        return "$base $suffix"
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(
            @Assisted pendingTrackIds: List<Long>,
            @Assisted suggestedName: String?,
        ): PlaylistsViewModel
    }
}
