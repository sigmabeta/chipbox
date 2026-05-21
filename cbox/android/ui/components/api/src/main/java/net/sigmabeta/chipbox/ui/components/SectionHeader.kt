package net.sigmabeta.chipbox.ui.components

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
import androidx.compose.ui.unit.dp


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
                    shape = SectionHeaderShape
                )
                .padding(horizontal = 8.dp)
                .semantics {
                    heading()
                }
        )
    }
}

private val SectionHeaderShape = RoundedCornerShape(4.dp)
