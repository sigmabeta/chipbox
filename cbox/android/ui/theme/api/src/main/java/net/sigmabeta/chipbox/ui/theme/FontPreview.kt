package net.sigmabeta.chipbox.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.ui.fonts.ChipboxFont

// Gaius Van Baelsar — The Praetorium, FFXIV: A Realm Reborn
private const val GAIUS_SPEECH = """Hmph! How very glib. And do you believe in Eorzea? Eorzea's unity is forged of falsehoods. Its city-states are built on deceit. And its faith is an instrument of deception.

It is naught but a cobweb of lies. To believe in Eorzea is to believe in nothing. In Eorzea, the beast tribes often summon gods to fight in their stead--though your comrades only rarely respond in kind. Which is strange, is it not?"""

private class ChipboxFontProvider : PreviewParameterProvider<ChipboxFont> {
    override val values = ChipboxFont.entries.asSequence()
}

@Composable
private fun FontPreviewContent(font: ChipboxFont) {
    AppTheme(brand = font, plain = font) {
        Surface {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "— titleLarge —",
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = font.fontName,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = font.description,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = GAIUS_SPEECH,
                    style = MaterialTheme.typography.titleLarge,
                )
                HorizontalDivider()
                Text(
                    text = "— bodyMedium —",
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = font.fontName,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = font.description,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = GAIUS_SPEECH,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Preview(name = "Light", showBackground = true, widthDp = 400)
@Preview(name = "Dark", showBackground = true, widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FontPreview(@PreviewParameter(ChipboxFontProvider::class) font: ChipboxFont) {
    FontPreviewContent(font)
}
