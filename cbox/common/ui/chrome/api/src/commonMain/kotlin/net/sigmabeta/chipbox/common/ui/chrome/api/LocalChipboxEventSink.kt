package net.sigmabeta.chipbox.common.ui.chrome.api

import androidx.compose.runtime.staticCompositionLocalOf
import net.sigmabeta.chipbox.appcomm.ChipboxEvent

/**
 * Per-screen sink for [ChipboxEvent]s emitted by feature ViewModels. Provided at the appui
 * level and refined inside each Voyager tab so that `NavigateTo`/`NavigateBack` push/pop the
 * local tab Navigator while system events (snackbar/clipboard/openUrl/picker) bubble to the
 * app-level handler. Consumed inside the anonymous Voyager `Screen` wrappers that adapt
 * each feature's `XxxRoute(onEvent)` to the parameter-free `Screen.Content()` contract.
 */
val LocalChipboxEventSink = staticCompositionLocalOf<(ChipboxEvent) -> Unit> {
    error("LocalChipboxEventSink not provided")
}
