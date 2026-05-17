package net.sigmabeta.chipbox.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.list.ListState
import net.sigmabeta.sage.list.ListStateActual
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

/**
 * Base ViewModel for any Chipbox screen rendered through [ChipboxListEntry] (i.e. a SAGE
 * [ListState] driving either [net.sigmabeta.sage.android.ui.list.ListScreen] or
 * [net.sigmabeta.sage.android.ui.list.GridScreen]).
 *
 * Subclasses provide their concrete [ListState] subtype as [S], an initial instance, and a
 * [handleAction] implementation. They mutate state via [updateState] and emit user intent by
 * calling [sendAction] (since this class implements [ActionSink], it can be passed directly
 * to composables / [ChipboxListEntry]).
 *
 * This is the deliberately-thin replacement for SAGE's `ListViewModelBrain`: it keeps the
 * parts that earned their keep (immutable [ListState], `toActual(stringProvider)` derivation,
 * a single typed action funnel) and drops the rest (scheduler, analytics, event SharedFlow,
 * dual `internalUiState`/`internalUiStateActual` flows). When a screen needs async data,
 * launch into [viewModelScope] directly and call [updateState] from the result.
 *
 * @param S the concrete [ListState] subtype this view model owns
 * @param initialState the state emitted before any actions are handled
 * @param stringProvider used by [ListState.toActual] to resolve [net.sigmabeta.sage.ui.SageStringId]s
 *   into display strings each time state changes
 * @param hatchet logger; every action is logged at verbose level before [handleAction] runs
 */
abstract class ChipboxListViewModel<S : ListState>(
    initialState: S,
    private val stringProvider: StringProvider,
    private val hatchet: Hatchet,
) : ViewModel(),
    ActionSink {

    private val _state = MutableStateFlow(initialState)

    /** The raw, typed state. Prefer this in tests; UI consumes [uiStateActual]. */
    val state: StateFlow<S> = _state.asStateFlow()

    /**
     * The rendered state ([ListStateActual]) — title, list items, column type — derived from
     * [state] by calling [ListState.toActual] on every emission. This is what [ChipboxListEntry]
     * (and through it [ListScreen]/[GridScreen]) collects.
     *
     * Started [SharingStarted.Eagerly] so the first composition has a value without a frame
     * of empty content. The seed [initialValue] is the same `toActual` of `initialState`.
     */
    val uiStateActual: StateFlow<ListStateActual> = _state
        .map { it.toActual(stringProvider) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = initialState.toActual(stringProvider),
        )

    private val _showDebug = MutableStateFlow(false)

    /**
     * Whether to render diagnostic overlays in [ListModel] composables (e.g. error stack traces
     * in `ErrorStateListModel`). Always false today; will be wired to a debug setting later.
     */
    val showDebug: StateFlow<Boolean> = _showDebug.asStateFlow()

    private val _events = MutableSharedFlow<ChipboxEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * One-shot effects produced by this view model — navigation, external-URL launches,
     * snackbar messages, etc. Collected by [ChipboxListEntry], which forwards each event to
     * the host's `onEvent` lambda (see [net.sigmabeta.chipbox.ui.list.ChipboxListEntry]).
     */
    val events: SharedFlow<ChipboxEvent> = _events.asSharedFlow()

    /**
     * Emit a one-shot effect. Subclasses call this from [handleAction] instead of touching a
     * `NavController` (or a `SnackbarHostState`) directly, keeping the view model framework-free.
     */
    protected fun emit(event: ChipboxEvent) {
        _events.tryEmit(event)
    }

    /**
     * Apply [updater] to the current state and publish the result. The lambda runs synchronously
     * on the caller's thread — wrap any blocking work in a [viewModelScope] coroutine before
     * calling this.
     */
    protected fun updateState(updater: (S) -> S) {
        _state.update(updater)
    }

    /**
     * Receives every action emitted by composables in this screen (via [ActionSink]). Logs the
     * action and dispatches to [handleAction]. Final so subclasses can't accidentally skip the
     * log line — override [handleAction] instead.
     */
    final override fun sendAction(action: SageAction) {
        hatchet.v("${this::class.simpleName} action: $action")
        handleAction(action)
    }

    /**
     * Handle a single user/system action. Subclasses typically `when (action)` over their own
     * sealed action hierarchy plus relevant [SageAction] entries (e.g. `Resume`, `DeviceBack`)
     * and ignore the rest. Reach state changes via [updateState].
     */
    protected abstract fun handleAction(action: SageAction)
}
