package net.sigmabeta.chipbox.features.playbackstatus.real

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.debuginfo.DebugInfoManager
import net.sigmabeta.chipbox.ui.list.ChipboxListViewModel
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

@HiltViewModel
class PlaybackStatusViewModel @Inject constructor(
    private val debugInfoManager: DebugInfoManager,
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxListViewModel<PlaybackStatusState>(
    PlaybackStatusState(),
    stringProvider,
    hatchet,
) {
    init {
        viewModelScope.launch {
            debugInfoManager.debugInfo().collect { debug ->
                updateState { it.copy(debug = debug) }
            }
        }
    }

    override fun handleAction(action: SageAction) = Unit
}
