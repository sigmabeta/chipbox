package net.sigmabeta.chipbox.ui.components.api

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import net.sigmabeta.chipbox.ui.components.api.previews.FullScreenOf

@Preview
@Composable
private fun Light() {
    FullScreenOf { paddingValues ->
        Sample(paddingValues)
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Dark() {
    FullScreenOf(darkTheme = true) { paddingValues ->
        Sample(paddingValues)
    }
}

@Composable
private fun Sample(paddingValues: PaddingValues) {
    SectionHeader(
        "Sick new skills",
        modifier = Modifier,
        padding = paddingValues,
    )
}
