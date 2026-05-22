package net.sigmabeta.chipbox.common.appui.api

import androidx.compose.runtime.staticCompositionLocalOf
import net.sigmabeta.sage.appcomm.ActionSink

/**
 * App-level [ActionSink] for shell-wide `SageAction`s — currently the two back actions:
 * `AppBack` (the TopAppBar up arrow) and `DeviceBack` (Android system back, routed in via
 * each tab Navigator's `onBackPressed`). Provided in [ChipboxTabsScreen], where both the
 * active tab Navigator and the outer ChipboxEvent sink are in scope, so the handler can
 * decide *which* navigator to pop.
 *
 * This is the single place the shell turns a back gesture into navigation: the affordances
 * only report *what happened* (`AppBack` / `DeviceBack`); the sink decides the navigation.
 * Distinct from a per-screen `ActionSink` (the feature VM) and from [LocalChipboxEventSink],
 * which carries already-decided navigation events.
 */
internal val LocalAppActionSink = staticCompositionLocalOf<ActionSink> {
    error("LocalAppActionSink not provided")
}
