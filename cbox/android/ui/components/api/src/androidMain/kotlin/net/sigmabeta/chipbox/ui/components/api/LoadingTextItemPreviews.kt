package net.sigmabeta.chipbox.ui.components.api

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.sage.components.LoadingType
import net.sigmabeta.chipbox.ui.components.api.previews.FullScreenOf
import kotlin.random.Random

@Preview
@Composable
private fun Light() {
    FullScreenOf {
        Sample(
            seed = Random.nextLong(),
            loadingType = LoadingType.SINGLE_TEXT,
        )
    }
}

@Preview
@Composable
private fun LightWithImage() {
    FullScreenOf {
        Sample(
            seed = Random.nextLong(),
            loadingType = LoadingType.TEXT_IMAGE,
        )
    }
}

@Preview
@Composable
private fun LightWithImageAndCaption() {
    FullScreenOf {
        Sample(
            seed = Random.nextLong(),
            loadingType = LoadingType.TEXT_CAPTION_IMAGE,
        )
    }
}

@Preview
@Composable
private fun DarkWithImageAndCaption() {
    FullScreenOf(darkTheme = true) {
        Sample(
            seed = Random.nextLong(),
            loadingType = LoadingType.TEXT_CAPTION_IMAGE,
        )
    }
}

@Composable
private fun Sample(seed: Long, loadingType: LoadingType) {
    LoadingTextItem(
        loadingType = loadingType,
        seed = seed,
        modifier = Modifier,
        padding = PaddingValues(horizontal = 16.dp)
    )
}
