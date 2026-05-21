package net.sigmabeta.chipbox.ui.components.api

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import net.sigmabeta.chipbox.ui.components.api.previews.FullScreenOf
import kotlin.random.Random

@Preview
@Composable
private fun LightNotLoading() {
    FullScreenOf { paddingValues ->
        Sample(paddingValues)
    }
}

@Preview
@Composable
private fun Light() {
    val randomizer = Random(RANDOMIZER_SEED)
    FullScreenOf { paddingValues ->
        SampleLoading(randomizer.nextLong(), paddingValues)
    }
}

@Preview
@Composable
private fun Dark() {
    val randomizer = Random(RANDOMIZER_SEED)
    FullScreenOf(darkTheme = true) { paddingValues ->
        SampleLoading(randomizer.nextLong(), paddingValues)
    }
}

@Composable
private fun SampleLoading(
    seed: Long,
    paddingValues: PaddingValues,
) {
    LoadingSectionHeader(
        seed = seed,
        modifier = Modifier,
        padding = paddingValues,
    )
}

@Composable
private fun Sample(paddingValues: PaddingValues) {
    SectionHeader(
        "Sick new skills",
        modifier = Modifier,
        padding = paddingValues,
    )
}

private const val RANDOMIZER_SEED = 12301L
