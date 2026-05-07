package net.sigmabeta.chipbox.ui.components.subs

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import net.sigmabeta.chipbox.ui.components.previews.ChipboxTheme
import net.sigmabeta.chipbox.ui.components.previews.ChipboxThemeMenu
import net.sigmabeta.sage.ui.vector

@Composable
@Suppress("MagicNumber")
fun Rating(
    score: Int,
    modifier: Modifier,
) {
    Row(
        modifier = modifier
    ) {
        for (index in 1..4) {
            val icon = if (score >= index) {
                net.sigmabeta.sage.ui.Icon.FAVORITE_FILLED
            } else {
                net.sigmabeta.sage.ui.Icon.FAVORITE_EMPTY
            }

            Icon(
                imageVector = icon.vector(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Preview
@Composable
private fun Light() {
    ChipboxTheme {
        Box(
            modifier = Modifier.background(
                color = MaterialTheme.colorScheme.background
            )
        ) {
            Sample()
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Dark() {
    ChipboxTheme {
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
private fun Menu() {
    ChipboxThemeMenu {
        Box(
            modifier = Modifier.background(
                color = MaterialTheme.colorScheme.background
            )
        ) {
            Sample()
        }
    }
}

@Composable
@Suppress("MagicNumber")
private fun Sample() {
    Rating(
        3,
        Modifier
    )
}
