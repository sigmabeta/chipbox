package net.sigmabeta.chipbox.ui.components.api.subs

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp

@Composable
fun ElevatedCircle(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .aspectRatio(1.0f)
            .shadow(elevation = 4.dp, shape = CircleShape),
        content = content
    )
}
