package net.sigmabeta.chipbox.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun CrossfadeText(
    text: String,
    modifier: Modifier = Modifier,
    textModifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
) {
    Crossfade(
        targetState = text,
        modifier = modifier,
        label = "CrossfadeText",
    ) { current ->
        Text(
            text = current,
            color = color,
            style = style,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = overflow,
            textAlign = textAlign,
            modifier = textModifier,
        )
    }
}
