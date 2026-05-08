package net.sigmabeta.chipbox.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.ui.components.previews.FullScreenOf
import net.sigmabeta.chipbox.ui.components.subs.ElevatedPill
import net.sigmabeta.chipbox.ui.components.subs.Flasher
import net.sigmabeta.chipbox.ui.components.utils.nextPercentageFloat
import kotlin.random.Random

@Composable
@Suppress("MagicNumber")
fun LoadingSectionHeader(
    seed: Long,
    modifier: Modifier,
    padding: PaddingValues,
) {
    val randomizer = Random(seed)
    val randomDelay = randomizer.nextInt(200)
    ElevatedPill(
        modifier = modifier
            .padding(padding)
            .padding(bottom = 16.dp, top = 16.dp)
            .height(32.dp)
            .fillMaxWidth(
                randomizer.nextPercentageFloat(
                    minOutOfHundred = 30,
                    maxOutOfHundred = 80,
                )
            )
    ) {
        Flasher(startDelay = randomDelay)
    }
}

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
