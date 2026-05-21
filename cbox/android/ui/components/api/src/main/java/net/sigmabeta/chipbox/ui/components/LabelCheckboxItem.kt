package net.sigmabeta.chipbox.ui.components

import android.content.res.Configuration
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.ui.components.subs.LabeledThingy
import net.sigmabeta.chipbox.strings.ChipboxStringId
import net.sigmabeta.chipbox.strings.text
import net.sigmabeta.chipbox.ui.components.previews.ChipboxPreview
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.CheckableListModel

@Composable
fun LabelCheckboxItem(
    model: CheckableListModel,
    actionSink: ActionSink,
    modifier: Modifier,
    padding: PaddingValues,
) {
    val accyStateDescription = when (model.checked) {
        true -> ChipboxStringId.ACCY_ST_DESC_CHECKED.text()
        false -> ChipboxStringId.ACCY_ST_DESC_UNCHECKED.text()
        null -> ChipboxStringId.ACCY_ST_DESC_LOADING.text()
    }

    LabeledThingy(
        label = model.name,
        thingy = {
            Crossfade(
                targetState = model.checked,
                label = "CheckboxState"
            ) { checked ->
                when {
                    checked != null -> Checkbox(
                        checked = checked,
                        onCheckedChange = { actionSink.sendAction(model.clickAction) },
                        Modifier.clearAndSetSemantics { }
                    )

                    else -> CircularProgressIndicator(
                        modifier = Modifier
                            .padding(12.dp)
                            .size(24.dp)
                    )
                }
            }
        },
        onClick = { actionSink.sendAction(model.clickAction) },
        onClickLabel = ChipboxStringId.ACCY_OCL_CHECKBOX.text(),
        accyStateDescription = accyStateDescription,
        modifier = modifier,
        padding = padding,
    )
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
            SampleChecked()
            SampleUnchecked()
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
            SampleChecked()
            SampleUnchecked()
            SampleLoading()
        }
    }
}

@Composable
private fun SampleChecked() {
    var isChecked by remember { mutableStateOf(true) }

    Sample(
        "Sena seen in action",
        isChecked
    ) { isChecked = !isChecked }
}

@Composable
private fun SampleUnchecked() {
    var isChecked by remember { mutableStateOf(false) }

    Sample(
        "Pronounced \"Hydrocity\" correctly",
        isChecked
    ) { isChecked = !isChecked }
}

@Composable
private fun SampleLoading() {
    Sample(
        "Please wait, now loading...",
        null
    ) { }
}

@Composable
private fun Sample(name: String, isChecked: Boolean?, actionSink: ActionSink) {
    LabelCheckboxItem(
        CheckableListModel(
            name,
            name,
            isChecked,
            clickAction = SageAction.Noop,
        ),
        actionSink,
        Modifier,
        PaddingValues(horizontal = 8.dp)
    )
}
