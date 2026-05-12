package net.sigmabeta.chipbox.features.playbackstatus.real

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.ui.list.ChipboxListViewModel
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

@HiltViewModel
class PlaybackStatusViewModel @Inject constructor(
    private val director: Director,
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxListViewModel<PlaybackStatusState>(
    PlaybackStatusState(),
    stringProvider,
    hatchet,
) {
    init {
        viewModelScope.launch {
            director.metadataState().collect { track ->
                updateState { it.copy(track = track) }
            }
        }
        viewModelScope.launch {
            director.playbackState().collect { playback ->
                updateState { it.copy(playback = playback) }
            }
        }
        viewModelScope.launch {
            director.sessionState().collect { session ->
                updateState { it.copy(session = session) }
            }
        }
    }

    override fun handleAction(action: SageAction) = Unit
}
