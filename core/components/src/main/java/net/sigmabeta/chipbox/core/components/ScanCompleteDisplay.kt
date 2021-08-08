package net.sigmabeta.chipbox.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.components.R
import net.sigmabeta.chipbox.models.state.ScannerState

@Composable
fun ScanCompleteDisplay(
    scannerState: ScannerState.Complete,
    onClearClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(vertical = 8.dp)
    ) {
        ScanCompleteHeader(onClearClick)
        LabelValueRow(stringResource(R.string.scan_display_time), "${scannerState.timeInSeconds} seconds")
        LabelValueRow(stringResource(R.string.scan_display_games), "${scannerState.gamesFound}")
        LabelValueRow(stringResource(R.string.scan_display_tracks), "${scannerState.tracksFound}")
        LabelValueRow(stringResource(R.string.scan_display_errors), "${scannerState.tracksFailed}")
    }
}

@Preview
@Composable
fun PreviewScanComplete() {
    Box(
        modifier = Modifier.background(MaterialTheme.colors.primary)
    ) {
        ScanCompleteDisplay(
            scannerState = ScannerState.Complete(
                timeInSeconds = 47,
                gamesFound = 76,
                tracksFound = 182,
                tracksFailed = 2
            ),
        ) {}
    }
}