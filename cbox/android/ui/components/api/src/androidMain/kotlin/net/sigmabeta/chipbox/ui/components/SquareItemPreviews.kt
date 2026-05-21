package net.sigmabeta.chipbox.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.ui.components.previews.PreviewActionSink
import net.sigmabeta.chipbox.ui.components.previews.ChipboxPreview
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.SquareItemListModel
import net.sigmabeta.sage.ui.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


@Preview
@Composable
private fun Light() {
    ChipboxPreview {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
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
            modifier = Modifier
                .fillMaxSize()
                .background(
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
    var active by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SquareItem(
                SquareItemListModel(
                    dataId = 1234L,
                    name = "Xenoblade Chronicles 3",
                    sourceInfo = "https://randomfox.ca/images/12.jpg",
                    imagePlaceholder = Icon.Album,
                    actionableId = null,
                    clickAction = SageAction.Noop,
                    active = active,
                ),
                PreviewActionSink {},
                Modifier.weight(1.0f),
                PaddingValues(horizontal = 8.dp)
            )

            SquareItem(
                SquareItemListModel(
                    dataId = 1235L,
                    name = "Xenoblade Chronicles 3: Future Redeemed Some More",
                    sourceInfo = "https://randomfox.ca/images/1235.jpg",
                    imagePlaceholder = Icon.Album,
                    actionableId = null,
                    clickAction = SageAction.Noop,
                ),
                PreviewActionSink {},
                Modifier.weight(1.0f),
                PaddingValues(horizontal = 8.dp)
            )
        }
        Button(
            onClick = { active = !active },
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(text = if (active) "Deactivate first" else "Activate first")
        }
    }
}
