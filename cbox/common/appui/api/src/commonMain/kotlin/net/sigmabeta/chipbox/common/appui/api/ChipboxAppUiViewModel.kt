package net.sigmabeta.chipbox.common.appui.api

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
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
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.debug.DebugSettingsManager
import net.sigmabeta.chipbox.debug.ImageLoaderSource
import net.sigmabeta.chipbox.settings.ChipboxSettingsManager
import net.sigmabeta.chipbox.settings.ThemeMode
import net.sigmabeta.chipbox.ui.fonts.ChipboxFont
import net.sigmabeta.sage.appinfo.AppInfo
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class ChipboxAppUiViewModel @Inject constructor(
    settingsManager: ChipboxSettingsManager,
    debugSettingsManager: DebugSettingsManager,
    appInfo: AppInfo,
    private val hatchet: Hatchet,
) : ViewModel() {
    /**
     * Whether this is a debug build. Drives `ChipboxTheme`'s primary/secondary swap so debug
     * builds (Android debug + the always-debug desktop/web targets) are visually distinct from
     * release. Constant for the process, so a plain val rather than a flow.
     */
    val isDebugBuild: Boolean = appInfo.isDebug

    /** The user's persisted theme choice; drives `ChipboxTheme`'s light/dark scheme. */
    val themeMode: StateFlow<ThemeMode> = settingsManager.getThemeMode()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DEFAULT)

    /** The user's persisted brand/plain typefaces, applied by `ChipboxTheme`. */
    val brandFont: StateFlow<ChipboxFont> = settingsManager.getBrandFont()
        .map { ChipboxFont.fromStorageValue(it, ChipboxFont.DEFAULT_BRAND) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ChipboxFont.DEFAULT_BRAND)

    val plainFont: StateFlow<ChipboxFont> = settingsManager.getPlainFont()
        .map { ChipboxFont.fromStorageValue(it, ChipboxFont.DEFAULT_PLAIN) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ChipboxFont.DEFAULT_PLAIN)

    /**
     * Whether the UI should render generated gradient art instead of fetching real cover art —
     * driven by the debug "image loader" setting (FAKE). Unlike the audio/data source switches (DI
     * singletons read once at launch), this is a Compose-level toggle, so it applies live. Provided
     * as [net.sigmabeta.chipbox.common.ui.components.api.subs.LocalForceFakeImages] by the shell.
     */
    val forceFakeImages: StateFlow<Boolean> = debugSettingsManager.getImageLoaderSource()
        .map { it == ImageLoaderSource.FAKE }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _currentRoute = MutableStateFlow<String?>(null)

    /**
     * The route the user is currently looking at — pushed in by the shell's TabNavigator/
     * Navigator scope via [setCurrentRoute]. The value is the Voyager route key in the form
     * `"<tab key>/<deep screen key>"` (e.g. `"LibraryTab/GameDetail:42"`) or just `"<tab key>"`
     * while the tab root is the active screen. `null` until the first tab Navigator registers.
     *
     * Lets cross-cutting policy in this VM (e.g. deciding whether to honour a
     * [net.sigmabeta.chipbox.appcomm.ChipboxEvent.RequestMiniPlayerVisibility] from one screen
     * while another is on top) consult what's actually on screen without having to know
     * about Voyager.
     */
    val currentRoute: StateFlow<String?> = _currentRoute.asStateFlow()

    /** Called from the shell whenever the visible route changes. */
    fun setCurrentRoute(key: String?) {
        // Skip the log on no-op updates — the shell's [LaunchedEffect] is keyed on the
        // composed route string, so this should already be deduped, but guarding here
        // keeps the log clean if a future caller pushes redundantly.
        if (_currentRoute.value == key) return
        hatchet.v("${this::class.simpleName} route: ${_currentRoute.value} -> $key")
        _currentRoute.value = key
    }

    private val _effects = MutableSharedFlow<ChipboxEvent>(
        replay = 0,
        extraBufferCapacity = EFFECTS_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * Events the host (`ChipboxAppUi`) should apply — navigation, snackbars, chrome
     * mutations, etc. Populated by [handleEvent] after running any policy. A simple
     * pass-through today; the indirection means future cross-cutting decisions
     * (e.g. "ignore RequestMiniPlayerVisibility while NowPlaying is on top") live
     * here, in one place, instead of in the host's event-application layer.
     */
    val effects: SharedFlow<ChipboxEvent> = _effects.asSharedFlow()

    /**
     * Single entry point for every [ChipboxEvent] that escapes a screen. Subclasses of
     * policy go here; for now everything is forwarded verbatim to [effects].
     */
    fun handleEvent(event: ChipboxEvent) {
        hatchet.v("${this::class.simpleName} event: $event")
        // Future: filter / transform / drop based on `currentRoute` or other VM state.
        _effects.tryEmit(event)
    }
}

private const val EFFECTS_BUFFER = 64

/**
 * Exposes the singleton [ChipboxAppUiViewModel] to descendants of [ChipboxAppUi] —
 * specifically the shell ([ChipboxTabsScreen]), which lives inside Voyager and so resolves
 * its own [androidx.lifecycle.ViewModelStoreOwner]; without this local a `metroViewModel<…>()`
 * call from inside the shell would construct a *different* VM instance against that nested
 * store. Provided once in [ChipboxAppUi].
 */
internal val LocalChipboxAppUiViewModel = staticCompositionLocalOf<ChipboxAppUiViewModel> {
    error("LocalChipboxAppUiViewModel not provided")
}
