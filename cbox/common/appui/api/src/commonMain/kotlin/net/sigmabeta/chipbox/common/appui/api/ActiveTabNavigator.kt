package net.sigmabeta.chipbox.common.appui.api

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.TabNavigator

/**
 * Holds a reference to the currently-active tab's inner [Navigator] so the shell's back
 * handler ([LocalAppActionSink], which serves both the TopAppBar up arrow's `AppBack` and
 * the system back's `DeviceBack`) can pop a tab's deep stack from outside that tab's Content
 * scope. Each tab's `TabNavigatorContent` registers its Navigator via `DisposableEffect` on
 * mount and clears the registration on dispose — including tab switches, where Voyager
 * unmounts the old tab's UI before mounting the new one.
 *
 * Without this, the only way for the shell to pop the active tab would be through a back-press
 * dispatch into Voyager's internal handler, which isn't part of the public Voyager 1.1 API.
 */
@Stable
internal class ActiveTabNavigator {
    var navigator: Navigator? by mutableStateOf(null)

    /**
     * Route-key destinations backing the active tab's deep stack, keyed by the wrapped Screen's
     * [ScreenKey]. Voyager Screens are anonymous wrappers that don't carry the original route key;
     * the browser-history seam needs it to name the screen an in-app pop lands on. Entries are added
     * when the shell pushes a destination and are harmless to keep: each Screen key embeds its
     * arguments (`GameDetail:42`) and tab switches reset stacks to their roots.
     */
    val destinations: MutableMap<ScreenKey, Any> = mutableMapOf()
}

internal val LocalActiveTabNavigator = staticCompositionLocalOf<ActiveTabNavigator> {
    error("LocalActiveTabNavigator not provided")
}

/**
 * Holds a reference to the outer [TabNavigator] so the shell's back handler — created
 * *outside* the TabNavigator content block, where `tabNavigator` is not in scope — can
 * read the current tab and switch tabs. Mirrors [ActiveTabNavigator]; registered via
 * `DisposableEffect` inside the TabNavigator block.
 */
@Stable
internal class TabRouter {
    var tabNavigator: TabNavigator? by mutableStateOf(null)
}
