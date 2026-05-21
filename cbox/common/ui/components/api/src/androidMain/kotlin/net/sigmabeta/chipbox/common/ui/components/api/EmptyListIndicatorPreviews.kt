package net.sigmabeta.chipbox.common.ui.components.api

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import net.sigmabeta.chipbox.common.ui.components.api.previews.ChipboxPreview
import net.sigmabeta.sage.components.EmptyStateListModel
import net.sigmabeta.sage.components.ErrorStateListModel
import net.sigmabeta.sage.ui.Icon

@Preview
@Composable
private fun Light() {
    ChipboxPreview {
        Box(
            modifier = Modifier.background(
                color = MaterialTheme.colorScheme.background
            )
        ) {
            Sample()
        }
    }
}

@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun Dark() {
    ChipboxPreview {
        Box(
            modifier = Modifier.background(
                color = MaterialTheme.colorScheme.background
            )
        ) {
            Sample()
        }
    }
}

@Preview
@Composable
private fun LightError() {
    ChipboxPreview {
        Box(
            modifier = Modifier.background(
                color = MaterialTheme.colorScheme.background
            )
        ) {
            SampleError()
        }
    }
}

@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DarkError() {
    ChipboxPreview {
        Box(
            modifier = Modifier.background(
                color = MaterialTheme.colorScheme.background
            )
        ) {
            SampleError()
        }
    }
}

@Composable
private fun Sample() {
    EmptyListIndicator(
        EmptyStateListModel(
            icon = Icon.Album,
            explanation = "It's all part of the protocol, innit?",
            debugText = null,
            showCrossOut = true
        ),
        Modifier
    )
}

@Composable
private fun SampleError() {
    EmptyListIndicator(
        model = ErrorStateListModel(
            failedOperationName = "oops",
            errorString = "Enemy's broken away from me!",
            error = IllegalStateException("Could not maintain aggro. Try using provoke?"),
        ),
        showDebug = true,
        modifier = Modifier
    )
}
