package net.sigmabeta.chipbox.features.search.real

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import net.sigmabeta.chipbox.ui.components.api.Content
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.components.ListModel

/**
 * Custom search screen: a results grid (the standard SAGE [ListModel] pipeline) with the
 * [SearchBar] and a status-bar scrim overlaid on top — the same shape as VGLS's
 * SearchScreen, just rendering Chipbox list models.
 */
@Composable
fun SearchContent(
    listItems: ImmutableList<ListModel>,
    query: String,
    showDebug: Boolean,
    actionSink: ActionSink,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        val belowSearchBar: Dp = 96.dp
        val topInsets = WindowInsets.statusBars

        val contentPadding = WindowInsets(top = belowSearchBar)
            .add(topInsets)
            .add(WindowInsets(left = 16.dp, right = 16.dp))
            .add(WindowInsets.navigationBars)
            .asPaddingValues()

        val gridState = rememberLazyGridState()
        LaunchedEffect(query) { gridState.animateScrollToItem(0) }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(160.dp),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                items = listItems,
                key = { it.dataId },
                contentType = { it.layoutId() },
                span = {
                    if (it.columns < 1) {
                        GridItemSpan(maxLineSpan)
                    } else {
                        GridItemSpan(it.columns)
                    }
                },
            ) {
                it.Content(actionSink, showDebug, Modifier.animateItem(), PaddingValues())
            }
        }

        val topPaddingForSearchBar = topInsets.asPaddingValues().calculateTopPadding()

        SearchBar(
            text = query,
            actionSink = actionSink,
            modifier = Modifier.padding(top = topPaddingForSearchBar + 16.dp),
        )

        // Scrim for the Android status bar, so the search bar reads cleanly under it.
        Spacer(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(topPaddingForSearchBar.times(2))
                .background(
                    brush = Brush.verticalGradient(
                        colors = scrimColors(isSystemInDarkTheme()),
                    ),
                ),
        )
    }
}

@Suppress("MagicNumber")
private fun scrimColors(isDarkTheme: Boolean) = if (isDarkTheme) {
    listOf(
        Color(0, 0, 0, 160),
        Color(0, 0, 0, 64),
        Color(0, 0, 0, 0),
    )
} else {
    listOf(
        Color(255, 255, 255, 255),
        Color(255, 255, 255, 192),
        Color(255, 255, 255, 0),
    )
}
