package net.sigmabeta.chipbox.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import net.sigmabeta.chipbox.ui.components.previews.ChipboxPreview
import net.sigmabeta.sage.components.SubsectionHeaderListModel


@Preview
@Composable
private fun Light() {
    ChipboxPreview {
        Box(
            modifier = Modifier
                .background(color = MaterialTheme.colorScheme.primaryContainer)
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
                .background(color = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Sample()
        }
    }
}

@Composable
private fun Sample() {
    SubsectionHeader(
        SubsectionHeaderListModel(
            "Sick new skills",
        ),
        modifier = Modifier,
    )
}
