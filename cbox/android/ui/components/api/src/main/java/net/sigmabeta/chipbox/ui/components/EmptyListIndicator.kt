package net.sigmabeta.chipbox.ui.components

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.sigmabeta.sage.components.EmptyStateListModel
import net.sigmabeta.sage.components.ErrorStateListModel
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.SageMaterialVectors
import net.sigmabeta.sage.ui.icons.CrossOutColor
import net.sigmabeta.sage.ui.icons.IcCrossOut24dp
import net.sigmabeta.sage.ui.vector
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun EmptyListIndicator(
    model: ErrorStateListModel,
    modifier: Modifier,
    showDebug: Boolean,
    onBlack: Boolean = false,
) {
    EmptyListIndicator(
        explanation = model.errorString,
        icon = Icon.Warning,
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
                val backgroundColor = MaterialTheme.colorScheme.background
                val crossOutVector = remember(backgroundColor, color) {
                    SageMaterialVectors.IcCrossOut24dp(
                        mapOf(
                            CrossOutColor.Line to backgroundColor,
                            CrossOutColor.Halo to color,
                        )
                    )
                }
                Icon(
                    imageVector = crossOutVector,
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
    val baseStyle = MaterialTheme.typography.bodySmall
    val style = remember(baseStyle) {
        baseStyle.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
    Text(
        text = debugText,
        textAlign = TextAlign.Center,
        style = style,
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
    val baseStyle = MaterialTheme.typography.bodySmall
    val style = remember(baseStyle) {
        baseStyle.copy(fontSize = 6.sp, fontFamily = FontFamily.Monospace)
    }
    Text(
        text = debugText,
        textAlign = TextAlign.Center,
        style = style,
        color = color,
        modifier = Modifier
            .padding(horizontal = 32.dp)
            .padding(bottom = 16.dp)
            .widthIn(min = 200.dp, max = 400.dp)
            .alpha(0.8f)
    )
}
