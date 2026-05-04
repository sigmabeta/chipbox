package net.sigmabeta.chipbox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.SectionHeaderListModel
import net.sigmabeta.sage.components.SingleTextListModel

@Composable
fun ListItemContent(
    model: ListModel,
    sink: ActionSink,
    modifier: Modifier,
    padding: PaddingValues,
) {
    when (model) {
        is SectionHeaderListModel -> Text(
            text = model.title,
            style = MaterialTheme.typography.titleMedium,
            modifier = modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(top = 16.dp, bottom = 8.dp),
        )

        is SingleTextListModel -> Text(
            text = model.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier
                .fillMaxWidth()
                .clickable(enabled = model.clickAction !is SageAction.Noop) {
                    sink.sendAction(model.clickAction)
                }
                .padding(padding)
                .padding(vertical = 8.dp),
        )

        else -> Spacer(modifier = modifier)
    }
}
