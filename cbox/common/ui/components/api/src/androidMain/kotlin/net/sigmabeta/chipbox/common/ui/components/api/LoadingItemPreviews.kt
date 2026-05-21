package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import net.sigmabeta.chipbox.common.ui.components.api.previews.FullScreenOf
import net.sigmabeta.chipbox.common.ui.components.api.previews.PreviewActionSink
import net.sigmabeta.sage.ui.StringGenerator
import kotlinx.collections.immutable.toImmutableList
import net.sigmabeta.sage.components.HorizontalScrollerListModel
import net.sigmabeta.sage.components.LoadingItemListModel
import net.sigmabeta.sage.components.LoadingType
import java.util.Random
import kotlin.random.asKotlinRandom

@Preview
@Composable
private fun Light() {
    val randomizer = Random(RANDOMIZER_SEED)
    val stringGen = StringGenerator(randomizer)
    FullScreenOf { paddingValues ->
        Sample(
            randomizer = randomizer,
            paddingValues = paddingValues,
            stringGen = stringGen,
        )
    }
}

@Preview
@Composable
private fun Dark() {
    val randomizer = Random(RANDOMIZER_SEED)
    val stringGen = StringGenerator(randomizer)
    FullScreenOf(darkTheme = true) { paddingValues ->
        Sample(
            randomizer = randomizer,
            paddingValues = paddingValues,
            stringGen = stringGen,
        )
    }
}

@Composable
@Suppress("MagicNumber")
private fun ColumnScope.Sample(
    randomizer: Random,
    paddingValues: PaddingValues,
    stringGen: StringGenerator,
) {
    val possibleTypes = listOf(
        LoadingType.PAGE,
        LoadingType.SQUARE,
        LoadingType.NOTIF,
        LoadingType.WIDE_ITEM,
        LoadingType.BIG_IMAGE
    )

    val loadingType = possibleTypes.random(randomizer.asKotlinRandom())
    val rowName = loadingType.name
    LoadingSectionHeader(
        seed = randomizer.nextLong(),
        modifier = Modifier,
        padding = paddingValues
    )

    val items = List(randomizer.nextInt(5) + 5) { index ->
        LoadingItemListModel(
            loadingType,
            rowName,
            index,
        )
    }.toImmutableList()

    HorizontalScroller(
        model = HorizontalScrollerListModel(
            dataId = "$rowName.content".hashCode().toLong(),
            scrollingItems = items,
        ),
        actionSink = PreviewActionSink(),
        modifier = Modifier,
        showDebug = true,
        padding = paddingValues
    )
}

private const val RANDOMIZER_SEED = 1231L
private const val PAGE_MIN_WIDTH = 300
private const val PAGE_ASPECT_RATIO = 0.77272725f
