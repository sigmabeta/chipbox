package net.sigmabeta.chipbox.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.strings.text
import net.sigmabeta.chipbox.ui.components.previews.ChipboxPreview
import net.sigmabeta.chipbox.ui.components.previews.PreviewActionSink
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.LabelValueListModel
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
