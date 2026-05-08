package net.sigmabeta.chipbox.ui.components.subs

import android.os.Build
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp

@Composable
fun ElevatedCircle(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val commonModifier = modifier
        .padding(horizontal = 4.dp)
        .aspectRatio(1.0f)

    val actualModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        commonModifier
            .shadow(
                elevation = 4.dp,
                shape = CircleShape
            )
    } else {
        commonModifier.clip(CircleShape)
    }

    Surface(
        modifier = actualModifier,
        content = content
    )
}
