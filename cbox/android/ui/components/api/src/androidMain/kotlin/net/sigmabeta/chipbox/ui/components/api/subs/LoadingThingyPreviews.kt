package net.sigmabeta.chipbox.ui.components.api.subs

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.sigmabeta.chipbox.ui.components.api.previews.ChipboxPreview
import net.sigmabeta.chipbox.ui.components.api.previews.ChipboxPreviewMenu

@Preview
@Composable
private fun Light() {
    ChipboxPreview {
        Box(
            modifier = Modifier.background(
                color = MaterialTheme.colorScheme.background
            )
        ) {
            Sample()
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Dark() {
    ChipboxPreview {
        Box(
            modifier = Modifier.background(
                color = MaterialTheme.colorScheme.background
            )
        ) {
            Sample()
        }
    }
}

@Preview
@Composable
private fun Menu() {
    ChipboxPreviewMenu {
        Box(
            modifier = Modifier.background(
                color = MaterialTheme.colorScheme.background
            )
        ) {
            Sample()
        }
    }
}

@Composable
@Suppress("MagicNumber")
private fun Sample() {
    Column {
        LoadingThingy(
            thingy = {},
            modifier = Modifier
        )

        LoadingThingy(
            thingy = {
                Checkbox(
                    checked = false,
                    onCheckedChange = {}
                )
            },
            modifier = Modifier
        )

        LoadingThingy(
            thingy = {
                val simulatedTextHeight = with(LocalDensity.current) {
                    20.sp.toDp()
                }

                ElevatedPill(
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .height(simulatedTextHeight)
                        .weight(0.6f),
                    content = { Flasher() }
                )
            },
            modifier = Modifier
        )
    }
}
