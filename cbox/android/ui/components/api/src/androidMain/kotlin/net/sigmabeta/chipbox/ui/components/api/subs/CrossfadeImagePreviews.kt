package net.sigmabeta.chipbox.ui.components.api.subs

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.ui.components.api.ImageNameListItem
import net.sigmabeta.chipbox.ui.components.api.previews.PreviewActionSink
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.ImageNameListModel
import net.sigmabeta.sage.images.SourceInfo
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.chipbox.ui.components.api.previews.ChipboxPreview

@Preview
@Composable
private fun Light() {
    ChipboxPreview {
        Sample()
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Dark() {
    ChipboxPreview {
        Sample()
    }
}

@Composable
@Suppress("LongMethod", "MagicNumber")
private fun Sample() {
    Column(
        modifier = Modifier.background(
            color = MaterialTheme.colorScheme.background
        )
    ) {
        ImageNameListItem(
            ImageNameListModel(
                1234L,
                "Carrying the Weight of Life",
                SourceInfo(info = null),
                Icon.Description,
                null,
                clickAction = SageAction.Noop,
            ),
            PreviewActionSink { },
            Modifier,
            PaddingValues(horizontal = 8.dp)
        )

        Row {
            ElevatedRoundRect(
                modifier = Modifier
                    .size(64.dp)
                    .padding(8.dp),
                cornerRadius = 4.dp
            ) {
                CrossfadeImage(
                    sourceInfo = SourceInfo("etc"),
                    imagePlaceholder = Icon.Person,
                    contentDescription = null,
                    simulateError = true,
                    modifier = Modifier,
                )
            }

            ElevatedRoundRect(
                modifier = Modifier
                    .size(64.dp)
                    .padding(8.dp),
                cornerRadius = 4.dp
            ) {
                CrossfadeImage(
                    sourceInfo = SourceInfo(null),
                    imagePlaceholder = Icon.Description,
                    contentDescription = null,
                    modifier = Modifier,
                )
            }

            ElevatedRoundRect(
                modifier = Modifier
                    .size(64.dp)
                    .padding(8.dp),
                cornerRadius = 4.dp
            ) {
                CrossfadeImage(
                    sourceInfo = SourceInfo("doesn't matter"),
                    imagePlaceholder = Icon.Description,
                    contentDescription = null,
                    modifier = Modifier,
                )
            }
        }

        Row {
            ElevatedCircle(
                Modifier.size(64.dp)
            ) {
                CrossfadeImage(
                    sourceInfo = SourceInfo("etc"),
                    imagePlaceholder = Icon.Person,
                    contentDescription = null,
                    simulateError = true,
                    modifier = Modifier,
                )
            }

            ElevatedCircle(
                Modifier.size(64.dp)
            ) {
                CrossfadeImage(
                    sourceInfo = SourceInfo(null),
                    imagePlaceholder = Icon.Description,
                    contentDescription = null,
                    modifier = Modifier,
                )
            }

            ElevatedCircle(
                Modifier.size(64.dp)
            ) {
                CrossfadeImage(
                    sourceInfo = SourceInfo("doesn't matter"),
                    imagePlaceholder = Icon.Description,
                    contentDescription = null,
                    modifier = Modifier,
                )
            }
        }
    }
}
