package net.sigmabeta.chipbox.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.components.R

@Composable
fun ScanCompleteHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(
                horizontal = 16.dp,
                vertical = 8.dp
            )
    ) {
        Text(
            text = stringResource(R.string.scan_display_complete),
            style = MaterialTheme.typography.h5,
            modifier = Modifier.weight(1.0f),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.onPrimary
        )
    }
}

@Preview
@Composable
fun PreviewScanCompleteHeader() {
    Box(
        modifier = Modifier.background(MaterialTheme.colors.primary)
    ) {
        ScanCompleteHeader()
    }
}