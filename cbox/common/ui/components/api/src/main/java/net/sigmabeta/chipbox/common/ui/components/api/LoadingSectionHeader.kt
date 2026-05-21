package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.common.ui.components.api.subs.ElevatedPill
import net.sigmabeta.chipbox.common.ui.components.api.subs.Flasher
import net.sigmabeta.chipbox.common.ui.components.api.utils.nextPercentageFloat
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
