package net.sigmabeta.chipbox.features.playbackstatus.real

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.sigmabeta.chipbox.appcomm.ChipboxEvent

/**
 * `expect`/`actual` split for a reason of source-set visibility rather than platform
 * behaviour: the two actuals are identical (`metroViewModel<PlaybackStatusViewModel>()` +
 * [net.sigmabeta.chipbox.common.ui.list.api.ChipboxListEntry]). [PlaybackStatusViewModel]
 * lives in `jvmSharedMain` (it reaches the `jvmSharedMain`-resident `PlaybackDebugInfo` /
 * `VolumeProcessor` types), so it isn't visible from commonMain — but the shared
 * `screenFor()` in `cbox/common/appui/api` must call `PlaybackStatusRoute` from commonMain.
 * Declaring the route as `expect` here bridges that: the `actual`s in androidMain/jvmMain
 * see the VM, and commonMain consumers see this declaration.
 *
 * (Settings uses the same split for a genuine platform reason — its folder picker differs.
 * Here the bodies match; factor out if a third consumer ever appears.)
 */
@Composable
expect fun PlaybackStatusRoute(
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier = Modifier,
)
