package net.sigmabeta.chipbox.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.ui.components.previews.ChipboxPreview
import net.sigmabeta.sage.components.SubsectionHeaderListModel

@OptIn(ExperimentalTextApi::class)
@Composable
fun SubsectionHeader(
    model: SubsectionHeaderListModel,
    modifier: Modifier,
) {
    val title = remember(model.title) { model.title.uppercase() }
    val baseStyle = MaterialTheme.typography.bodyMedium
    val style = remember(baseStyle) { baseStyle.copy(fontFamily = SubsectionHeaderFontFamily) }

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

@OptIn(ExperimentalTextApi::class)
private val SubsectionHeaderFontFamily = FontFamily(
    Font(DeviceFontFamilyName("sans-serif-condensed")),
)

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
