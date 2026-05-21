package net.sigmabeta.chipbox.ui.components.api

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import net.sigmabeta.sage.components.CollapsibleDetailsListModel
import net.sigmabeta.chipbox.ui.components.api.previews.FullScreenOf
import net.sigmabeta.sage.ui.StringGenerator
import java.util.Random
import kotlinx.collections.immutable.toImmutableList

@Preview
@Composable
private fun Light() {
    val randomizer = Random(RANDOMIZER_SEED)
    val stringGen = StringGenerator(randomizer)

    FullScreenOf(darkTheme = false) { paddingValues ->
        Sample(paddingValues, randomizer, stringGen)
    }
}

@Preview
@Composable
private fun Dark() {
    val randomizer = Random(RANDOMIZER_SEED)
    val stringGen = StringGenerator(randomizer)

    FullScreenOf(darkTheme = true) { paddingValues ->
        Sample(paddingValues, randomizer, stringGen)
    }
}

@Composable
@Suppress("MagicNumber")
private fun Sample(padding: PaddingValues, randomizer: Random, stringGen: StringGenerator) {
    val detailCount = randomizer.nextInt(5)
    val detailItems = List(detailCount) {
        stringGen.generateLorem()
    }

    val title = stringGen.generateTitle()

    val model = CollapsibleDetailsListModel(
        dataId = title.hashCode().toLong(),
        title = title,
        detailItems = detailItems.toImmutableList(),
        initiallyCollapsed = detailCount % 2 == 0,
    )

    CollapsibleDetailsListItem(
        model = model,
        padding = padding,
        modifier = Modifier,
    )
}

private const val RANDOMIZER_SEED = 1231L
