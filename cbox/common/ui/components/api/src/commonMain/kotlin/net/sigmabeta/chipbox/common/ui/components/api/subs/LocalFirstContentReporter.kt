package net.sigmabeta.chipbox.common.ui.components.api.subs

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Invoked when the first content-bearing [net.sigmabeta.chipbox.common.ui.components.api.GridImage]
 * cell composes — i.e. the library grid is showing its first item.
 *
 * Hosts that measure startup provide a lambda that reports the app fully drawn (Android's
 * `Activity.reportFullyDrawn`), which the macrobenchmark's `StartupTimingMetric` records as
 * time-to-first-content. Every other host leaves it null, so this costs a null check per cell in
 * production.
 *
 * Provided near the top of the Compose tree by the host app; read by `GridImage`.
 */
val LocalFirstContentReporter = staticCompositionLocalOf<(() -> Unit)?> { null }
