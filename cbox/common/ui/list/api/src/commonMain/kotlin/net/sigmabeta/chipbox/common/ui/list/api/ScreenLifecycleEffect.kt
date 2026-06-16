package net.sigmabeta.chipbox.common.ui.list.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecycleResumeEffect
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.appcomm.SageAction

/**
 * The [LifecycleOwner] the screen entries observe for foreground/background, supplied by the host
 * shell (`ChipboxAppUi`) from the platform's `LocalLifecycleOwner`. Defaults to null so a Route
 * rendered outside the shell — an isolated Route-render test, a `@Preview` — simply receives no
 * lifecycle actions instead of crashing on the absent androidx `LocalLifecycleOwner` that the bare
 * test/preview scene never provides.
 */
val LocalScreenLifecycleOwner = staticCompositionLocalOf<LifecycleOwner?> { null }

/**
 * Centralized screen-lifecycle → active-VM plumbing. The shared entry composables every screen
 * routes through ([ChipboxListEntry] and `ChipboxFreeformEntry`) call this to feed the foreground
 * lifecycle into whatever [ActionSink] (the screen's ViewModel) they wrap, as plain actions:
 *
 * - ON_RESUME (including app foregrounding) dispatches [SageAction.Resume].
 * - ON_PAUSE / disposal dispatches [SageAction.Pause].
 *
 * Only the active (composed) screen's entry is in composition, so only it receives these. Screens
 * that don't care simply ignore them in `handleAction`. No-ops when no [LocalScreenLifecycleOwner]
 * is provided (outside the shell).
 */
@Composable
fun ScreenLifecycleEffect(actionSink: ActionSink) {
    val owner = LocalScreenLifecycleOwner.current ?: return
    LifecycleResumeEffect(owner, lifecycleOwner = owner) {
        actionSink.sendAction(SageAction.Resume)
        onPauseOrDispose { actionSink.sendAction(SageAction.Pause) }
    }
}
