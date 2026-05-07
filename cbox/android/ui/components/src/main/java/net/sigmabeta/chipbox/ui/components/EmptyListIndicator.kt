package net.sigmabeta.chipbox.ui.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.sigmabeta.chipbox.ui.components.previews.ChipboxPreview
import net.sigmabeta.sage.components.EmptyStateListModel
import net.sigmabeta.sage.components.ErrorStateListModel
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.vector

@Composable
fun EmptyListIndicator(
    model: ErrorStateListModel,
    modifier: Modifier,
    showDebug: Boolean,
    onBlack: Boolean = false,
) {
    EmptyListIndicator(
        explanation = model.errorString,
        icon = Icon.WARNING,
        showCrossOut = false,
        error = model.error,
        showDebug = showDebug,
        onBlack = onBlack,
        modifier = modifier,
    )
}

@Composable
fun EmptyListIndicator(
    model: EmptyStateListModel,
    modifier: Modifier,
    onBlack: Boolean = false,
) {
    EmptyListIndicator(
        explanation = model.explanation,
        icon = model.icon,
        showCrossOut = model.showCrossOut,
        onBlack = onBlack,
        modifier = modifier
    )
}

@Composable
@Suppress("LongMethod")
private fun EmptyListIndicator(
    explanation: String,
    icon: Icon,
    showCrossOut: Boolean,
    showDebug: Boolean = false,
    error: Throwable? = null,
    onBlack: Boolean,
    modifier: Modifier,
) {
    val color = if (onBlack) {
        Color.White
    } else {
        MaterialTheme.colorScheme.outline
    }
    var showDetails by remember { mutableStateOf(false) }

    val clickableModifier = if (showDebug) {
        modifier.clickable { showDetails = !showDetails }
    } else {
        modifier
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = clickableModifier
            .animateContentSize()
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Box(
            modifier = Modifier
                .padding(
                    top = 16.dp,
                    bottom = 8.dp
                )
        ) {
            Icon(
                imageVector = icon.vector(),
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .size(96.dp)
            )

            if (showCrossOut) {
                Icon(
                    imageVector = Icon.CROSSOUT.vector(),
                    tint = Color.Unspecified,
                    contentDescription = null,
                    modifier = Modifier
                        .size(96.dp)
                )
            }
        }

        Text(
            text = explanation,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .padding(bottom = 16.dp)
                .widthIn(min = 200.dp, max = 400.dp)
        )

        val shouldShowError = showDebug || LocalInspectionMode.current
        if (shouldShowError && error != null) {
            val firstStackElement = error.stackTrace.first()
            val className = firstStackElement.fileName
            val methodName = firstStackElement.methodName
            val lineNumber = firstStackElement.lineNumber
            val summary = "$className: $lineNumber ($methodName)"
            DebugText(summary, color)

            AnimatedVisibility(visible = !showDetails) {
                val message = error.message
                if (message != null) {
                    DebugTextSmall(message, color)
                }
            }

            AnimatedVisibility(visible = showDetails) {
                DebugTextSmall(error.stackTraceToString(), color)
            }
        }
    }
}

@Composable
private fun DebugText(debugText: String, color: Color) {
    Text(
        text = debugText,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        ),
        color = color,
        modifier = Modifier
            .padding(horizontal = 32.dp)
            .padding(bottom = 8.dp)
            .widthIn(min = 200.dp, max = 400.dp)
    )
}

@Composable
@Suppress("MagicNumber")
private fun DebugTextSmall(debugText: String, color: Color) {
    Text(
        text = debugText,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 6.sp,
            fontFamily = FontFamily.Monospace
        ),
        color = color,
        modifier = Modifier
            .padding(horizontal = 32.dp)
            .padding(bottom = 16.dp)
            .widthIn(min = 200.dp, max = 400.dp)
            .alpha(0.8f)
    )
}

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
            icon = Icon.ALBUM,
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
