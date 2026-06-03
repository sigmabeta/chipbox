package net.sigmabeta.chipbox.common.ui.freeform.api

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.freeform.FreeformState
import net.sigmabeta.sage.freeform.FreeformStateActual
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

/**
 * Base ViewModel for any Chipbox screen rendered through [ChipboxFreeformEntry] — i.e. a SAGE
 * [FreeformState] whose subclass supplies its own Compose layout via the entry's `content` slot,
 * rather than being decomposed into a list of `ListModel`s.
 *
 * Sibling to [net.sigmabeta.chipbox.common.ui.list.api.ChipboxListViewModel]; the only structural difference
 * is that [uiStateActual] is typed as [FreeformStateActual] of the subclass's [Model] type, so the
 * Compose entry can hand the rendered model directly to the screen without going through a
 * heterogeneous list pipeline.
 */
abstract class ChipboxFreeformViewModel<S : FreeformState<Model>, Model>(
    initialState: S,
    private val stringProvider: StringProvider,
    private val hatchet: Hatchet,
) : ViewModel(),
    ActionSink {

    private val _state = MutableStateFlow(initialState)

    /** The raw, typed state. Prefer this in tests; UI consumes [uiStateActual]. */
    val state: StateFlow<S> = _state.asStateFlow()

    /**
     * The rendered state — title plus the subclass's [Model] — derived from [state] by calling
     * [FreeformState.toActual] on every emission. Collected by [ChipboxFreeformEntry], which
     * wires the title bar and hands [FreeformStateActual.content] to the screen's `content` slot.
     */
    val uiStateActual: StateFlow<FreeformStateActual<Model>> = _state
        .map { it.toActual(stringProvider) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = initialState.toActual(stringProvider),
        )

    private val _showDebug = MutableStateFlow(false)

    /** Whether to render diagnostic overlays in the screen's content. Always false today. */
    val showDebug: StateFlow<Boolean> = _showDebug.asStateFlow()

    // A Channel rather than a replay=0 SharedFlow so a one-shot event emitted *before* the UI
    // starts collecting isn't lost. Subclasses commonly emit from flow collectors launched in
    // their `init` block — which run as the ViewModel is constructed, before
    // [ChipboxFreeformEntry]'s `LaunchedEffect` subscribes to [events]. A SharedFlow with no
    // replay drops those; a buffered Channel holds them until the first collector drains it.
    // (Concretely: NowPlaying fires NavigateBack the instant it sees an IDLE/STOPPED player, e.g.
    // when Android recreates the screen against a fresh, sessionless Director after a process
    // kill.) Single-consumer is fine — exactly one collector forwards these to the host.
    private val _events = Channel<ChipboxEvent>(Channel.BUFFERED)

    /** One-shot effects. Collected by [ChipboxFreeformEntry] and forwarded to the host. */
    val events: Flow<ChipboxEvent> = _events.receiveAsFlow()

    protected fun emit(event: ChipboxEvent) {
        _events.trySend(event)
    }

    protected fun updateState(updater: (S) -> S) {
        _state.update(updater)
    }

    final override fun sendAction(action: SageAction) {
        hatchet.v("${this::class.simpleName} action: $action")
        handleAction(action)
    }

    protected abstract fun handleAction(action: SageAction)
}
