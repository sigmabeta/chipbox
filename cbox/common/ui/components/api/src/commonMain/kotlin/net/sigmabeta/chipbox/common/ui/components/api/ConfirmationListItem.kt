package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.common.ui.components.api.utils.FocusAreaShape
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.ConfirmationListModel

// Lifted like the EditTextListItem container — this is a transient prompt that should stand out.
private val CONFIRM_ELEVATION = 4.dp
private val CONFIRM_LIFT_PADDING = 16.dp
private val CONFIRM_CONTENT_PADDING = 12.dp

/**
 * Renderer for a [ConfirmationListModel]: a lifted container with a header, a body line, and a
 * cancel/confirm button pair. Confirm dispatches [SageAction.ConfirmationConfirmed] with the model's
 * id; cancel dispatches [SageAction.ConfirmationCancelled] with the id.
 */
@Composable
fun ConfirmationListItem(
    model: ConfirmationListModel,
    actionSink: ActionSink,
    modifier: Modifier,
    padding: PaddingValues,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = FocusAreaShape,
        shadowElevation = CONFIRM_ELEVATION,
        tonalElevation = CONFIRM_ELEVATION,
        modifier = modifier.padding(CONFIRM_LIFT_PADDING),
    ) {
        Column(modifier = Modifier.padding(padding)) {
            CrossfadeText(
                text = model.header,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textModifier = Modifier.padding(
                    start = CONFIRM_CONTENT_PADDING,
                    end = CONFIRM_CONTENT_PADDING,
                    top = CONFIRM_CONTENT_PADDING,
                ),
            )

            CrossfadeText(
                text = model.bodyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textModifier = Modifier.padding(
                    start = CONFIRM_CONTENT_PADDING,
                    end = CONFIRM_CONTENT_PADDING,
                    top = 4.dp,
                ),
            )

            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(CONFIRM_CONTENT_PADDING),
            ) {
                TextButton(onClick = { actionSink.sendAction(SageAction.ConfirmationCancelled(model.id)) }) {
                    CrossfadeText(text = model.cancelLabel)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { actionSink.sendAction(SageAction.ConfirmationConfirmed(model.id)) }) {
                    CrossfadeText(text = model.confirmLabel)
                }
            }
        }
    }
}
