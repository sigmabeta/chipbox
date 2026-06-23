package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
    // AnimatedContent (not Crossfade) so both the outgoing and incoming text stay aligned to this
    // anchor while the container resizes. Crossfade pins both layers to the container's top-start,
    // which makes centered text render flush-left mid-animation when the two strings differ in width.
    contentAlignment: Alignment = Alignment.TopStart,
) {
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentAlignment = contentAlignment,
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
