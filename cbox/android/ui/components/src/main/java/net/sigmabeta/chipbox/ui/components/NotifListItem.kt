package net.sigmabeta.chipbox.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.ui.components.previews.NotifConstants
import net.sigmabeta.chipbox.ui.components.previews.PreviewActionSink
import net.sigmabeta.chipbox.ui.components.R
import net.sigmabeta.chipbox.ui.components.previews.ChipboxTheme
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

@Preview
@Composable
private fun Light() {
    ChipboxTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color = MaterialTheme.colorScheme.background)
        ) {
            Sample()
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Dark() {
    ChipboxTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color = MaterialTheme.colorScheme.background)
        ) {
            Sample()
        }
    }
}

@Composable
@Suppress("MagicNumber", "MaxLineLength", "LongMethod")
private fun Sample() {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(dimensionResource(id = R.dimen.margin_side))
    ) {
        NotifListItem(
            NotifListModel(
                1234L,
                "This App Is Cool",
                "Here's what you need to know about how cool this app is. I might render on two lines. Isn't that awesome?",
                "Find out more",
                SageAction.Noop,
                false
            ),
            PreviewActionSink { },
            modifier = Modifier
        )

        NotifListItem(
            NotifListModel(
                1234L,
                "Really Long Notif",
                "Here's what you need to know about how cool this app is. I might render on two lines. Or even on three. heck, we might do four. Sky's the limit. Isn't that awesome?",
                "That sure is long, all right",
                SageAction.Noop,
                false
            ),
            PreviewActionSink { },
            modifier = Modifier
        )

        NotifListItem(
            NotifListModel(
                1234L,
                "This Notif Has No Action",
                "Here's what you need to know about how cool this app is. I might render on two lines. Isn't that awesome?",
                "Find out more",
                null,
                false
            ),
            PreviewActionSink { },
            modifier = Modifier
        )

        NotifListItem(
            NotifListModel(
                1234L,
                "Situation Very Wrong",
                "Everything is broken!",
                "Fix it",
                SageAction.Noop,
                true
            ),
            PreviewActionSink { },
            modifier = Modifier
        )
    }
}
