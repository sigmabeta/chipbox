package net.sigmabeta.chipbox.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
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
import net.sigmabeta.sage.components.WideItemListModel
import net.sigmabeta.sage.ui.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


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
@Suppress("MagicNumber")
private fun Sample() {
    var active by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.background(
            color = MaterialTheme.colorScheme.background
        )
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .wrapContentSize()
                .padding(16.dp)
        ) {
            WideItem(
                WideItemListModel(
                    dataId = 1234L,
                    name = "Konami Kukeiha Club",
                    sourceInfo = "https://randomfox.ca/images/12.jpg",
                    imagePlaceholder = Icon.Person,
                    actionableId = null,
                    clickAction = SageAction.Noop,
                    active = active,
                ),
                PreviewActionSink {},
                modifier = Modifier,
                padding = PaddingValues(horizontal = 8.dp)
            )

            WideItem(
                WideItemListModel(
                    dataId = 1235L,
                    name = "Masayoshi Soken",
                    sourceInfo = "https://randomfox.ca/images/12.jpg",
                    imagePlaceholder = Icon.Person,
                    actionableId = null,
                    clickAction = SageAction.Noop,
                ),
                PreviewActionSink {},
                modifier = Modifier,
                padding = PaddingValues(horizontal = 8.dp)
            )
        }
        Button(
            onClick = { active = !active },
            modifier = Modifier.padding(8.dp),
        ) {
            Text(text = if (active) "Deactivate first" else "Activate first")
        }
    }
}
