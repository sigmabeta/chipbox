package net.sigmabeta.chipbox.common.ui.components.api.subs

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * When true, [CrossfadeImage] renders the deterministic generated gradient ([FakeImage]) instead of
 * fetching through Coil — the same path `LocalInspectionMode` triggers for previews/Paparazzi, but
 * driven by the debug "image loader" setting (and defaulted on in UI tests). Defaults to false so
 * production renders real cover art unless a host opts in.
 *
 * Provided once near the top of the Compose tree (the app shell reads the setting; the UI-test
 * harness forces it on); read by [CrossfadeImage].
 */
val LocalForceFakeImages = staticCompositionLocalOf { false }
