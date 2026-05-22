package net.sigmabeta.chipbox.common.appui.api

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * App-level shared [SnackbarHostState] hoisted to [ChipboxAppUi] so the outer ChipboxEvent
 * sink and [ChipboxTabsScreen]'s Scaffold share one snackbar queue: the sink (in scope at
 * the outer Navigator) enqueues, and the Scaffold's snackbar slot renders. Hoisting it above
 * the [TabNavigator] keeps the host alive across tab switches and deep navigation within a
 * tab (including [NowPlayingScreen]).
 */
internal val LocalAppSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("LocalAppSnackbarHostState not provided")
}
