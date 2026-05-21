package net.sigmabeta.chipbox.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.ui.components.previews.PreviewActionSink
import net.sigmabeta.chipbox.ui.components.previews.ChipboxPreview
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.NotifListModel


@Preview
@Composable
private fun Light() {
    ChipboxPreview {
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
    ChipboxPreview {
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
