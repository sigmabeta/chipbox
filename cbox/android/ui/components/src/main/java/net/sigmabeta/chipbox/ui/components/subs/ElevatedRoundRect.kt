package net.sigmabeta.chipbox.ui.components.subs

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ElevatedRoundRect(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val actualModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        modifier.shadow(
                elevation = 4.dp,
                shape = shape
            )
    } else {
        modifier.clip(shape)
    }

    Surface(
        modifier = actualModifier,
        content = content
    )
}
