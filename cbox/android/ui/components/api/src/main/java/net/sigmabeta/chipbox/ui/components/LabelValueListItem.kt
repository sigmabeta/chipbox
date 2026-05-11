package net.sigmabeta.chipbox.ui.components

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.ui.components.previews.PreviewActionSink
import net.sigmabeta.chipbox.ui.components.subs.ElevatedPill
import net.sigmabeta.chipbox.ui.components.subs.Flasher
import net.sigmabeta.chipbox.ui.components.subs.LabeledThingy
import net.sigmabeta.chipbox.ui.components.utils.nextPercentageFloat
import net.sigmabeta.chipbox.strings.ChipboxStringId
import net.sigmabeta.chipbox.strings.id
import net.sigmabeta.chipbox.ui.components.previews.ChipboxPreview
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
    val action = model.clickAction
    val onClickLabel = if (action !is SageAction.Noop) {
        stringResource(ChipboxStringId.ACCY_OCL_VALUE.id())
    } else {
        null
    }

    val labelColor by animateColorAsState(
        targetValue = if (model.active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onBackground
        },
        label = "LabelValueListItem.labelColor",
    )
    val fontWeightValue by animateIntAsState(
        targetValue = if (model.active) FontWeight.Bold.weight else FontWeight.Normal.weight,
        label = "LabelValueListItem.fontWeight",
    )
    val fontWeight = FontWeight(fontWeightValue)

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
                TextValue(value = value!!, active = model.active)
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
    val valueColor by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onTertiaryContainer
        },
        label = "TextValue.color",
    )
    val fontWeightValue by animateIntAsState(
        targetValue = if (active) FontWeight.Bold.weight else FontWeight.Normal.weight,
        label = "TextValue.fontWeight",
    )

    Text(
        text = value,
        textAlign = TextAlign.End,
        style = MaterialTheme.typography.labelLarge,
        color = valueColor,
        fontWeight = FontWeight(fontWeightValue),
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

@Preview
@Composable
private fun Light() {
    ChipboxPreview {
        Column(
            modifier = Modifier.background(
                color = MaterialTheme.colorScheme.background
            )
        ) {
            Sample()
            SampleLoading()
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Dark() {
    ChipboxPreview {
        Column(
            modifier = Modifier.background(
                color = MaterialTheme.colorScheme.background
            )
        ) {
            Sample()
            SampleLoading()
        }
    }
}

@Composable
private fun Sample() {
    var active by remember { mutableStateOf(false) }

    Column {
        LabelValueListItem(
            LabelValueListModel(
                label = "Days which are training days",
                value = "Every",
                clickAction = SageAction.Noop,
                active = active,
            ),
            PreviewActionSink {},
            Modifier,
            PaddingValues(horizontal = 8.dp)
        )
        Button(
            onClick = { active = !active },
            modifier = Modifier.padding(8.dp),
        ) {
            Text(text = if (active) "Deactivate" else "Activate")
        }
    }
}

@Composable
private fun SampleLoading() {
    LabelValueListItem(
        LabelValueListModel(
            "Please wait, now loading...",
            null,
            SageAction.Noop
        ),
        PreviewActionSink {},
        Modifier,
        PaddingValues(horizontal = 8.dp)
    )
}
