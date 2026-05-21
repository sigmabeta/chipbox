package net.sigmabeta.chipbox.ui.components.subs

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.getValue


@Composable
fun Flasher(
    startDelay: Int = 0
) {
    val infiniteTransition = rememberInfiniteTransition()
    val animatedAlphaValue by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 500 - (startDelay / 3),
                delayMillis = startDelay,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        )
    )

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .alpha(animatedAlphaValue),
        color = MaterialTheme.colorScheme.inverseSurface,
    ) { }
}
