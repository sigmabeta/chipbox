package net.sigmabeta.chipbox.common.ui.components.api.subs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import net.sigmabeta.sage.images.SourceInfo
import kotlin.random.Random

@Composable
internal actual fun FakeImage(sourceInfo: SourceInfo, modifier: Modifier) {
    // Like the desktop actual (no android.graphics BitmapGenerator on this enforcement-only
    // target): draw an equivalent deterministic gradient with multiplatform Compose graphics.
    val brush = remember(sourceInfo.info) {
        val rng = Random(sourceInfo.info.hashCode())
        Brush.linearGradient(
            listOf(Color(rng.nextInt()), Color(rng.nextInt()), Color(rng.nextInt())),
        )
    }
    Box(modifier.background(brush))
}
