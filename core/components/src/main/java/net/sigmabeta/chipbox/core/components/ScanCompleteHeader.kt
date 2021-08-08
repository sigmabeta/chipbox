package net.sigmabeta.chipbox.core.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.components.R

@Composable
fun ScanCompleteHeader(onClearClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_check_24),
            contentDescription = stringResource(id = R.string.cont_desc_scan_complete),
            modifier = Modifier
                .height(64.dp)
                .padding(
                    vertical = 8.dp,
                    horizontal = 16.dp
                )
                .aspectRatio(1.0f)
                .align(CenterVertically)
                .clip(CircleShape)
                .background(MaterialTheme.colors.secondary)
        )

        Text(
            text = stringResource(R.string.scan_display_complete),
            style = MaterialTheme.typography.h5,
            color = MaterialTheme.colors.onPrimary,
            modifier = Modifier
                .weight(1.0f)
                .align(CenterVertically)
        )
        
        Image(
            painter = painterResource(id = R.drawable.ic_clear_24),
            contentDescription = stringResource(id = R.string.cont_desc_scan_clear),
            modifier = Modifier
                .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                .height(48.dp)
                .aspectRatio(1.0f)
                .padding(6.dp)
                .align(CenterVertically)
                .clickable(onClick = onClearClick)
        )
    }
}

@Preview
@Composable
fun PreviewScanCompleteHeader() {
    Box(
        modifier = Modifier.background(MaterialTheme.colors.primary)
    ) {
        ScanCompleteHeader {

        }
    }
}