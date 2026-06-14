package net.sigmabeta.chipbox.common.appui.api

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.Flow

/**
 * A stream of route-key destinations to open *inside the active tab* — the same place a feature
 * screen's `NavigateTo` lands, so the screen renders with the tabs chrome (TopAppBar + nav bar)
 * around it. Provided by [ChipboxAppUi] from its `activeTabDestinations` parameter; [ChipboxTabs-
 * Screen] collects it and pushes each destination onto the active tab's Navigator via `screenFor`.
 *
 * The shell already routes in-tab navigation through the per-tab `LocalChipboxEventSink` when a
 * screen emits it from within the tab; this local is the seam for driving the same navigation from
 * *outside* a screen's composition — a UI test starting at a deep screen, or a future deep-link /
 * session-restore opening one at launch. `null` (the default) means no such stream is wired.
 */
internal val LocalActiveTabDestinations = staticCompositionLocalOf<Flow<Any>?> { null }

/**
 * Invoked with each route-key destination as the shell navigates to it (the active tab's deep
 * pushes — feature `NavigateTo` and the [LocalActiveTabDestinations] seam). Provided by
 * [ChipboxAppUi] from its `onNavigate` parameter; a UI test records these to assert that an
 * interaction triggered the expected navigation. `null` (the default) means nobody's observing.
 */
internal val LocalNavigationObserver = staticCompositionLocalOf<((Any) -> Unit)?> { null }
