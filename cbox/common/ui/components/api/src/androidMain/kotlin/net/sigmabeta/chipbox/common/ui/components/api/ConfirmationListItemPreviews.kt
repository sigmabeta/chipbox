package net.sigmabeta.chipbox.common.ui.components.api

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.common.ui.components.api.previews.ChipboxPreview
import net.sigmabeta.chipbox.common.ui.components.api.previews.PreviewActionSink
import net.sigmabeta.sage.components.ConfirmationListModel

@Preview
@Composable
private fun Light() {
    ChipboxPreview {
        Column(modifier = Modifier.background(color = MaterialTheme.colorScheme.background)) {
            Sample()
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Dark() {
    ChipboxPreview {
        Column(modifier = Modifier.background(color = MaterialTheme.colorScheme.background)) {
            Sample()
        }
    }
}

@Composable
private fun Sample() {
    ConfirmationListItem(
        model = ConfirmationListModel(
            id = 1L,
            header = "Delete playlist?",
            bodyText = "This can't be undone.",
            confirmLabel = "Delete",
        ),
        actionSink = PreviewActionSink {},
        modifier = Modifier,
        padding = PaddingValues(horizontal = 8.dp),
    )
}
