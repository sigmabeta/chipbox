package net.sigmabeta.chipbox.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.ui.components.previews.PreviewActionSink
import net.sigmabeta.chipbox.ui.components.previews.ChipboxPreview
import kotlinx.collections.immutable.toImmutableList
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.HorizontalScrollerListModel
import net.sigmabeta.sage.components.ImageNameListModel
import net.sigmabeta.sage.components.LoadingItemListModel
import net.sigmabeta.sage.components.LoadingType
import net.sigmabeta.sage.components.SquareItemListModel
import net.sigmabeta.sage.components.WideItemListModel
import net.sigmabeta.sage.images.SourceInfo
import net.sigmabeta.sage.ui.Icon
import java.util.Random

@Composable
@Suppress("MagicNumber")
fun HorizontalScroller(
    model: HorizontalScrollerListModel,
    actionSink: ActionSink,
    showDebug: Boolean,
    modifier: Modifier,
    padding: PaddingValues,
) {
    LazyRow(
        contentPadding = padding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        items(
            items = model.scrollingItems,
            key = { it.dataId },
            contentType = { it.javaClass.simpleName },
        ) {
            it.Content(
                sink = actionSink,
                debug = showDebug,
                mod = Modifier.animateItem(),
                pad = PaddingValues()
            )
        }
    }
}

@Preview
@Composable
private fun Light() {
    ChipboxPreview {
        Sample()
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Dark() {
    ChipboxPreview {
        Sample()
    }
}

@Composable
@Suppress("MagicNumber")
private fun Sample() {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .fillMaxSize()
            .background(
                color = MaterialTheme.colorScheme.background
            )
    ) {
        val rng = Random("HorizontalScroller".hashCode().toLong())

        val paddingModifier = PaddingValues(
            horizontal = dimensionResource(id = R.dimen.margin_side)
        )
        SquareItemSection(rng, paddingModifier)
        WideItemSection(rng, paddingModifier)
        LoadingSquareItemSection(rng, paddingModifier)
        VerticalSection(rng, paddingModifier)
        SquareItemSection(rng, paddingModifier)
        VerticalSection(rng, paddingModifier)
    }
}

@Composable
@Suppress("MagicNumber")
private fun SquareItemSection(rng: Random, padding: PaddingValues) {
    SectionHeader(
        name = "Square Items",
        modifier = Modifier,
        padding = padding,
    )

    HorizontalScroller(
        model = HorizontalScrollerListModel(
            dataId = 1_000_000L,
            scrollingItems = List(15) { index ->
                SquareItemListModel(
                    dataId = index.toLong(),
                    name = "Square #$index",
                    sourceInfo = rng.nextInt().toString(),
                    imagePlaceholder = Icon.ALBUM,
                    null,
                    clickAction = SageAction.Noop,
                )
            }.toImmutableList()
        ),
        PreviewActionSink { },
        modifier = Modifier,
        showDebug = true,
        padding = padding
    )
}

@Composable
@Suppress("MagicNumber")
private fun LoadingSquareItemSection(rng: Random, padding: PaddingValues) {
    val sectionName = "Square Items"
    LoadingSectionHeader(
        seed = sectionName.hashCode().toLong(),
        modifier = Modifier,
        padding = padding,
    )

    HorizontalScroller(
        model = HorizontalScrollerListModel(
            dataId = 1_000_000L,
            scrollingItems = List(15) { index ->
                LoadingItemListModel(
                    loadingType = LoadingType.SQUARE,
                    loadOperationName = sectionName,
                    loadPositionOffset = index,
                )
            }.toImmutableList()
        ),
        PreviewActionSink { },
        showDebug = true,
        modifier = Modifier,
        padding = padding
    )
}

@Composable
@Suppress("MagicNumber")
private fun WideItemSection(rng: Random, padding: PaddingValues) {
    SectionHeader(
        name = "Wide Items",
        modifier = Modifier,
        padding = padding,
    )

    HorizontalScroller(
        model = HorizontalScrollerListModel(
            dataId = 1_000L,
            scrollingItems = List(15) { index ->
                WideItemListModel(
                    dataId = index.toLong(),
                    name = "Wide Item #$index",
                    sourceInfo = rng.nextInt().toString(),
                    Icon.PERSON,
                    null,
                    clickAction = SageAction.Noop
                )
            }.toImmutableList()
        ),
        PreviewActionSink { },
        showDebug = true,
        modifier = Modifier,
        padding = padding
    )
}

@Composable
@Suppress("MagicNumber")
private fun VerticalSection(rng: Random, padding: PaddingValues) {
    SectionHeader(
        name = "Vertically Scrolling Items",
        modifier = Modifier,
        padding = padding,
    )

    repeat(3) { index ->
        ImageNameListItem(
            model = ImageNameListModel(
                dataId = index.toLong(),
                name = "Wide Item #$index",
                sourceInfo = SourceInfo(rng.nextInt().toString()),
                Icon.DESCRIPTION,
                null,
                clickAction = SageAction.Noop
            ),
            PreviewActionSink { },
            modifier = Modifier,
            padding = padding,
        )
    }
}
