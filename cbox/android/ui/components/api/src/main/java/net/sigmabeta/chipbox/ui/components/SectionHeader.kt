package net.sigmabeta.chipbox.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.ui.components.previews.FullScreenOf

@Composable
fun SectionHeader(
    name: String,
    modifier: Modifier,
    padding: PaddingValues,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(padding)
            .padding(bottom = 16.dp, top = 16.dp)
    ) {
        val color = MaterialTheme.colorScheme.onBackground

        Text(
            text = name,
            style = MaterialTheme.typography.titleLarge,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .border(
                    width = 2.dp,
                    color = color,
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 8.dp)
                .semantics {
                    heading()
                }
        )
    }
}

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
