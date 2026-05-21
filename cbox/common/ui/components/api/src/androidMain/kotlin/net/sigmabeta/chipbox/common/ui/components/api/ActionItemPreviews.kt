package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import net.sigmabeta.chipbox.common.ui.components.api.previews.PreviewActionSink
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.CtaListModel
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.chipbox.common.ui.components.api.previews.ChipboxPreview
import net.sigmabeta.chipbox.common.ui.components.api.previews.ChipboxPreviewMenu

@Preview
@Composable
private fun Selected() {
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
private fun NotMenu() {
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
private fun Sample() {
    ActionItem(
        CtaListModel(
            Icon.FavoriteEmpty,
            "Find a path to the future",
            SageAction.Noop,
        ),
        PreviewActionSink { },
        padding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier
    )
}
