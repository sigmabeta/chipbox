package net.sigmabeta.chipbox.ui.components.api

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.ui.components.api.previews.ChipboxPreview
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.CheckableListModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
