package net.sigmabeta.chipbox.features.rescanstatus

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.sigmabeta.chipbox.appcomm.ChipboxEvent

/**
 * `expect`/`actual` only so the `actual` (and the [RescanStatusViewModel] it resolves) can live in
 * `jvmSharedMain` alongside the Scanner-dependent view model — Scanner's types aren't visible in
 * commonMain. There's no platform-specific UI here; a single actual serves Android and JVM.
 */
@Composable
expect fun RescanStatusRoute(
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier = Modifier,
)
