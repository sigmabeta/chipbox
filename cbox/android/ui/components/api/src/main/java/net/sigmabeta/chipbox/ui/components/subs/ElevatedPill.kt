package net.sigmabeta.chipbox.ui.components.subs

import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.ui.components.previews.ChipboxPreview

@Composable
fun ElevatedPill(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = CircleShape
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

@Preview
@Composable
private fun Light() {
    ChipboxPreview {
        Box(
            modifier = Modifier
                .background(color = MaterialTheme.colorScheme.background)
                .padding(4.dp)
        ) {
            Sample()
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Dark() {
    ChipboxPreview {
        Box(
            modifier = Modifier
                .background(color = MaterialTheme.colorScheme.background)
                .padding(4.dp)
        ) {
            Sample()
        }
    }
}

@Composable
private fun Sample() {
    ElevatedPill(
        Modifier
            .height(48.dp)
            .width(256.dp)
    ) {
        Flasher()
    }
}
