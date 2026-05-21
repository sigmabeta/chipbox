package net.sigmabeta.chipbox.ui.components.api

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.ui.components.api.previews.NotifConstants
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.NotifListModel

@Composable
@Suppress("LongMethod")
fun NotifListItem(
    model: NotifListModel,
    actionSink: ActionSink,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .widthIn(
                min = NotifConstants.MIN_WIDTH,
                max = NotifConstants.MAX_WIDTH,
            )
    ) {
        val (cardColor, contentColor, buttonTextColor) = if (model.isError) {
            Triple(
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
                MaterialTheme.colorScheme.tertiary
            )
        } else {
            Triple(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer,
                MaterialTheme.colorScheme.primary
            )
        }

        Column(
            modifier = Modifier
                .background(cardColor)
                .padding(top = 16.dp, bottom = 24.dp)
                .padding(horizontal = 24.dp)
        ) {
            Row {
                Text(
                    text = model.title,
                    color = contentColor,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.CenterVertically)
                )

                IconButton(
                    onClick = { actionSink.sendAction(SageAction.NotifClearClicked(model.dataId)) },
                    modifier = Modifier
                        .height(48.dp)
                        .width(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        tint = contentColor,
                        contentDescription = null,
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Text(
                text = model.description,
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium
            )

            val action = model.action
            if (action != null) {
                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                TextButton(
                    onClick = { actionSink.sendAction(action) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = model.actionLabel,
                        color = buttonTextColor
                    )
                }
            }
        }
    }
}
