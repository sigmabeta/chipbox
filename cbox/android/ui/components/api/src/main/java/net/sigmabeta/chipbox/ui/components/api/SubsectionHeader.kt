package net.sigmabeta.chipbox.ui.components.api

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.sigmabeta.sage.components.SubsectionHeaderListModel

@Composable
fun SubsectionHeader(
    model: SubsectionHeaderListModel,
    modifier: Modifier,
) {
    val title = remember(model.title) { model.title.uppercase() }
    val baseStyle = MaterialTheme.typography.bodyMedium
    val style = remember(baseStyle) { baseStyle.copy(fontFamily = FontFamily.SansSerif) }

    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Text(
            text = title,
            style = style,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 4.dp,
                    horizontal = 8.dp
                )
        )
    }
}
