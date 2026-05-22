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
    // Desktop has no android.graphics BitmapGenerator; draw an equivalent deterministic
    // gradient with Compose graphics. Only hit under LocalInspectionMode (desktop previews) —
    // never at app runtime, where CrossfadeImage takes the real Coil path.
    val brush = remember(sourceInfo.info) {
        val rng = Random(sourceInfo.info.hashCode())
        Brush.linearGradient(
            listOf(Color(rng.nextInt()), Color(rng.nextInt()), Color(rng.nextInt())),
        )
    }
    Box(modifier.background(brush))
}
