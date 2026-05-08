package net.sigmabeta.chipbox.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.ui.fonts.ChipboxFont
import net.sigmabeta.chipbox.ui.theme.tokens.ChipboxTypefaceTokens
import net.sigmabeta.chipbox.ui.theme.tokens.toFontFamily

@Composable
private fun FontPreviewContent() {
    val fonts = ChipboxFont.entries

    fonts
    AppTheme {
        Surface {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FontSample(null, null)
                fonts.forEach {
                    val fontFamily = it.toFontFamily()
                    FontSample(fontFamily, it)
                }
            }
        }
    }
}

@Composable
private fun FontPreviewContentStaticText() {
    val fonts = ChipboxFont.entries

    fonts
    AppTheme {
        Surface {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FontSampleStaticText(null)
                fonts.forEach {
                    val fontFamily = it.toFontFamily()
                    FontSampleStaticText(fontFamily, it.scaleFactor)
                }
            }
        }
    }
}

@Composable
private fun FontSample(
    fontFamily: FontFamily?,
    font: ChipboxFont?,
) {
    val brandFont = fontFamily ?: ChipboxTypefaceTokens.Brand
    val plainFont = fontFamily ?: ChipboxTypefaceTokens.Plain
    AppTheme(brandFont = brandFont, plainFont = plainFont, fontScale = font?.scaleFactor ?: 1.0f) {
        Text(
            text = font?.fontName ?: "Default Material",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = font?.description ?: "The quick brown fox jumped over stuff.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Spacer(
        modifier = Modifier.height(4.dp)
    )
}

@Composable
private fun FontSampleStaticText(
    fontFamily: FontFamily?,
    scaleFactor: Float = 1.0f,
) {
    val brandFont = fontFamily ?: ChipboxTypefaceTokens.Brand
    val plainFont = fontFamily ?: ChipboxTypefaceTokens.Plain
    AppTheme(brandFont = brandFont, plainFont = plainFont, fontScale = scaleFactor) {
        Text(
            text = "Lorem Ipsum Tertium Est",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "The quick brown fox jumped over the ugly duckling.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Spacer(
        modifier = Modifier.height(4.dp)
    )
}

@Preview(name = "Light", showBackground = true, widthDp = 400)
@Preview(name = "Dark", showBackground = true, widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FontPreview() {
    FontPreviewContent()
}

@Preview(name = "Light", showBackground = true, widthDp = 400)
@Preview(name = "Dark", showBackground = true, widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FontPreviewStaticText() {
    FontPreviewContentStaticText()
}
