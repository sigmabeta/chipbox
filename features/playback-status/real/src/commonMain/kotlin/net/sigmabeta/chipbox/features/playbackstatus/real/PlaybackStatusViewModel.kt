package net.sigmabeta.chipbox.features.playbackstatus.real

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.debuginfo.DebugInfoManager
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListViewModel
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class PlaybackStatusViewModel @Inject constructor(
    private val debugInfoManager: DebugInfoManager,
    stringProvider: StringProvider,
    private val hatchet: Hatchet,
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

    override fun handleAction(action: SageAction) {
        when (action) {
            PlaybackStatusAction.CopyDebugInfoClicked -> onCopyDebugInfoClicked()
            else -> Unit
        }
    }

    private fun onCopyDebugInfoClicked() {
        val dump = state.value.debug?.toString() ?: "No debug info collected yet."
        hatchet.i("PlaybackDebugInfo dump:\n$dump")
        emit(ChipboxEvent.CopyToClipboard(label = CLIPBOARD_LABEL, text = dump))
    }

    private companion object {
        const val CLIPBOARD_LABEL = "Chipbox debug info"
    }
}
