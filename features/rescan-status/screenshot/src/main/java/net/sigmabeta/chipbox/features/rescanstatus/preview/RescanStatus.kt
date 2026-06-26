package net.sigmabeta.chipbox.features.rescanstatus.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import net.sigmabeta.chipbox.features.rescanstatus.RescanStatusState
import net.sigmabeta.chipbox.features.rescanstatus.ScanEventItem
import net.sigmabeta.chipbox.features.rescanstatus.ScanEventKind
import net.sigmabeta.chipbox.features.rescanstatus.ScanPhase
import net.sigmabeta.chipbox.ui.previews.DevicePreviews
import net.sigmabeta.chipbox.ui.previews.ListScreenPreview
import net.sigmabeta.chipbox.ui.previews.previewWidthClass
import net.sigmabeta.sage.list.WidthClass

@DevicePreviews
@Composable
internal fun RescanStatus(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = scanningState(),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

@DevicePreviews
@Composable
internal fun RescanStatusIdle(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = RescanStatusState(),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

private fun scanningState() = RescanStatusState(
    phase = ScanPhase.SCANNING,
    timeInSeconds = 7,
    gamesFound = 3,
    tracksFound = 42,
    tracksFailed = 1,
    currentFolder = "Mega Man 2",
    currentFile = "02 - title.nsf",
    // Chronological (oldest first); the screen reverses for display, so Mega Man 2 shows on top.
    events = listOf(
        ScanEventItem(1L, "Final Fantasy VI", ScanEventKind.REMOVED, 0, gameId = null, imageUrl = null),
        ScanEventItem(2L, "Chrono Trigger", ScanEventKind.UPDATED, 21, gameId = 102L, imageUrl = null),
        ScanEventItem(3L, "Mega Man 2", ScanEventKind.ADDED, 18, gameId = 101L, imageUrl = null),
    ),
)
