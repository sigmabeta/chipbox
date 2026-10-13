package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.common.ui.components.api.subs.ElevatedPill
import net.sigmabeta.chipbox.common.ui.components.api.subs.Flasher
import net.sigmabeta.chipbox.common.ui.components.api.subs.LabeledThingy
import net.sigmabeta.chipbox.common.ui.components.api.subs.TintedText
import net.sigmabeta.chipbox.common.ui.components.api.utils.nextPercentageFloat
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.chipbox.strings.api.text
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.LabelValueListModel
import kotlin.random.Random

@Composable
fun LabelValueListItem(
    model: LabelValueListModel,
    actionSink: ActionSink,
    modifier: Modifier,
    padding: PaddingValues,
) {
    val value = model.value
    // AnimatedVisibility keeps composing its content through the exit transition, so when [value]
    // flips to null on a track change the `value != null` block below still runs for a frame. Hold
    // the last non-null value and render it during that exit, instead of `value!!` (which NPE'd).
    val lastValue = remember { mutableStateOf(value) }
    if (value != null) {
        lastValue.value = value
    }
    val action = model.clickAction
    val onClickLabel = if (action !is SageAction.Noop) {
        ChipboxStringId.ACCY_OCL_VALUE.text()
    } else {
        null
    }

    val labelColor = animateColorAsState(
        targetValue = if (model.active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onBackground
        },
        label = "LabelValueListItem.labelColor",
    )
    // Snapped, not animated: animating FontWeight re-measures the text every frame.
    val fontWeight = if (model.active) FontWeight.Bold else FontWeight.Normal

    LabeledThingy(
        label = model.label,
        thingy = {
            AnimatedVisibility(
                visible = value == null
            ) {
                LoadingTextValue(model)
            }
            AnimatedVisibility(
                visible = value != null
            ) {
                lastValue.value?.let { TextValue(value = it, active = model.active) }
            }
        },
        onClick = { actionSink.sendAction(model.clickAction) },
        onClickLabel = onClickLabel,
        accyStateDescription = null, // It already reads out both strings
        modifier = modifier,
        padding = padding,
        labelColor = labelColor,
        labelFontWeight = fontWeight,
    )
}

@Composable
fun TextValue(value: String, active: Boolean = false) {
    val valueColor = animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onTertiaryContainer
        },
        label = "TextValue.color",
    )
    // Snapped, not animated: animating FontWeight re-measures the text every frame.
    val fontWeight = if (active) FontWeight.Bold else FontWeight.Normal

    TintedText(
        text = value,
        textAlign = TextAlign.End,
        style = MaterialTheme.typography.labelLarge,
        color = valueColor,
        fontWeight = fontWeight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .padding(vertical = 16.dp)
    )
}

@Composable
@Suppress("MagicNumber")
private fun LoadingTextValue(model: LabelValueListModel) {
    val randomizer = Random(model.label.hashCode())
    val randomDelay = randomizer.nextInt(200)

    ElevatedPill(
        modifier = Modifier
            .padding(vertical = 16.dp)
            .height(14.dp)
            .fillMaxWidth(
                randomizer.nextPercentageFloat(
                    minOutOfHundred = 10,
                    maxOutOfHundred = 30,
                )
            )
    ) {
        Flasher(startDelay = randomDelay)
    }
}
