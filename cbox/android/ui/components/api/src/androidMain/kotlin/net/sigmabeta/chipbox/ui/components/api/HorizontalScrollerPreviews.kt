package net.sigmabeta.chipbox.ui.components.api

import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import net.sigmabeta.chipbox.ui.components.api.previews.PreviewActionSink
import net.sigmabeta.chipbox.ui.components.api.previews.ChipboxPreview
import kotlinx.collections.immutable.toImmutableList
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
            horizontal = 16.dp
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
                    imagePlaceholder = Icon.Album,
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
                    Icon.Person,
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
                Icon.Description,
                null,
                clickAction = SageAction.Noop
            ),
            PreviewActionSink { },
            modifier = Modifier,
            padding = padding,
        )
    }
}
