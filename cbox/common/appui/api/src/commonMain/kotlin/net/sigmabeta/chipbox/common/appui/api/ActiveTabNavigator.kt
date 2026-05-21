package net.sigmabeta.chipbox.common.appui.api

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import cafe.adriel.voyager.navigator.Navigator

/**
 * Holds a reference to the currently-active tab's inner [Navigator] so the chrome
 * ([ChipboxTabsScreen]'s TopAppBar back arrow, primarily) can pop a tab's deep stack
 * from outside that tab's Content scope. Each tab's `TabNavigatorContent` registers its
 * Navigator via `DisposableEffect` on mount and clears the registration on dispose —
 * including tab switches, where Voyager unmounts the old tab's UI before mounting the
 * new one.
 *
 * Without this, the only way for the chrome to pop the active tab would be through a
 * back-press dispatch into Voyager's internal handler, which isn't part of the public
 * Voyager 1.1 API. Hardware back already works because Voyager hooks the Android back
 * dispatcher directly per Navigator.
 */
@Stable
internal class ActiveTabNavigator {
    var navigator: Navigator? by mutableStateOf(null)
}

internal val LocalActiveTabNavigator = staticCompositionLocalOf<ActiveTabNavigator> {
    error("LocalActiveTabNavigator not provided")
}
