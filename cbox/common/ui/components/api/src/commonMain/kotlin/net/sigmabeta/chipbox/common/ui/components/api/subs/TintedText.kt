package net.sigmabeta.chipbox.common.ui.components.api.subs

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/**
 * A [Text] whose colour is painted from a [State] in the **draw** phase instead of being read
 * during composition.
 *
 * The wrapped [Text] lays out normally — so sizing, `paddingFromBaseline`, `maxLines`,
 * `textAlign` and `overflow` behave exactly as before — but draws transparent, and the draw
 * phase repaints the reported [TextLayoutResult] with `color.value` via [drawText]. Because the
 * read happens inside `drawWithContent`, an animation tick invalidates only drawing: never
 * composition, and never text layout. That is what lets list rows / grid cells animate their
 * text colour without paying a full recomposition (and a text re-layout) every frame — the
 * reason it exists instead of `color = animateColorAsState(...).value`.
 *
 * Intended for components rendered inside scrolling containers (list rows, grid cells). For
 * one-shot / non-scrolling text, [Text] or [CrossfadeText] is a better fit.
 */
@Composable
fun TintedText(
    text: String,
    color: State<Color>,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        color = Color.Transparent,
        style = style,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
        onTextLayout = { layout = it },
        modifier = modifier.drawWithContent {
            val result = layout
            if (result != null) {
                drawText(result, color = color.value)
            } else {
                drawContent()
            }
        },
    )
}
