package net.sigmabeta.chipbox.appui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * App-level shared [SnackbarHostState] hoisted to [ChipboxAppUi] so screens at any depth of
 * the outer Voyager Navigator — both [ChipboxTabsScreen] (consumed by its Scaffold) and the
 * full-screen [NowPlayingScreen] overlay (consumed by its own snackbar slot) — enqueue
 * snackbars onto the same queue. Without this, snackbars triggered while NowPlaying is on
 * top would have no host to render them (the tabs' Scaffold isn't composed then).
 */
internal val LocalAppSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("LocalAppSnackbarHostState not provided")
}
