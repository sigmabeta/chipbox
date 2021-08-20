package net.sigmabeta.chipbox.core.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.components.R

@Composable
fun ScanFailedDisplay(path: String) {
    Row(
        modifier = Modifier
            .height(72.dp)
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 8.dp,
            )
    ) {
        Image(
            painter = painterResource(R.drawable.ic_error_24),
            contentDescription = null,
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1.0f)
        )

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1.0f)
                .padding(
                    start = 8.dp,
                    top = 8.dp,
                    bottom = 4.dp
                )
        ) {
            Text(
                text = "Scan Failed",
                color = MaterialTheme.colors.onPrimary,
                style = MaterialTheme.typography.subtitle1,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            )

            Text(
                text = path,
                color = MaterialTheme.colors.onPrimary,
                style = MaterialTheme.typography.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            )
        }
    }
}