package net.sigmabeta.chipbox.common.ui.components.api

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.toImmutableList
import net.sigmabeta.chipbox.common.ui.components.api.previews.ChipboxPreview
import net.sigmabeta.chipbox.common.ui.components.api.previews.PreviewActionSink
import net.sigmabeta.sage.components.DropdownSettingListModel

@Preview
@Composable
private fun Light() {
    ChipboxPreview {
        Box(
            modifier = Modifier.background(
                color = MaterialTheme.colorScheme.background
            )
        ) {
            Sample(false)
        }
    }
}

@Preview
@Composable
private fun LightExpanded() {
    ChipboxPreview {
        Box(
            modifier = Modifier.background(
                color = MaterialTheme.colorScheme.background
            )
        ) {
            Sample(true)
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
            Sample(false)
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DarkExpanded() {
    ChipboxPreview {
        Box(
            modifier = Modifier.background(
                color = MaterialTheme.colorScheme.background
            )
        ) {
            Sample(true)
        }
    }
}

@Suppress("MagicNumber")
@Composable
private fun Sample(expanded: Boolean) {
    ExpandingDropdownListItem(
        model = DropdownSettingListModel.ofLabels(
            settingId = "",
            name = "Who the bus is",
            selectedPosition = 3,
            labels = listOf(
                "Noah",
                "Lanz",
                "Taion",
                "Eunie",
                "Mio",
                "Sena",
            ).toImmutableList(),
            expanded = expanded,
        ),
        actionSink = PreviewActionSink(),
        modifier = Modifier,
        padding = PaddingValues(horizontal = 8.dp)
    )
}
