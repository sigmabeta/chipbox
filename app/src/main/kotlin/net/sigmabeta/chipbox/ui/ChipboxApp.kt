package net.sigmabeta.chipbox.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.sigmabeta.sage.android.ui.list.ListScreen
import net.sigmabeta.chipbox.feature.welcome.WelcomeViewModelBrain

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChipboxApp(brain: WelcomeViewModelBrain) {
    val state by brain.uiStateActual.collectAsStateWithLifecycle()

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text(state.title.title.orEmpty()) })
            },
        ) { padding ->
            ListScreen(
                state = state,
                actionSink = brain::sendAction,
                showDebug = false,
                sideMargin = 16.dp,
                modifier = Modifier.padding(padding),
                itemContent = { model, sink, debug, mod, pad ->
                    ListItemContent(model, sink, mod, pad)
                },
            )
        }
    }
}
