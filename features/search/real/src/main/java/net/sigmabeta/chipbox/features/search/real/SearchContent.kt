package net.sigmabeta.chipbox.features.search.real

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.ui.components.ImageNameCaptionListItem
import net.sigmabeta.chipbox.ui.components.ImageNameListItem
import net.sigmabeta.chipbox.ui.components.SearchHistoryListItem
import net.sigmabeta.sage.appcomm.ActionSink

@Composable
@Suppress("LongMethod")
internal fun SearchContent(
    model: SearchModel,
    actionSink: ActionSink,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        val enoughToGetBelowSearchBar: Dp = 96.dp
        val topInsets = WindowInsets.statusBars
        val sidePadding = WindowInsets(left = 16.dp, right = 16.dp)

        val contentPadding = WindowInsets(top = enoughToGetBelowSearchBar)
            .add(topInsets)
            .add(sidePadding)
            .asPaddingValues()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        ) {
            // Until a query is *submitted* (recorded + run against the DB), show
            // recent searches. After submit, show the games/songs/artists results.
            val noResults = model.gameItems.isEmpty() &&
                model.songItems.isEmpty() &&
                model.artistItems.isEmpty()
            if (model.submittedQuery.isBlank()) {
                Text(
                    text = model.emptyPrompt,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                model.historyItems.forEach { item ->
                    SearchHistoryListItem(
                        model = item,
                        actionSink = actionSink,
                        modifier = Modifier,
                        padding = PaddingValues(),
                    )
                }
            } else if (model.searching && noResults) {
                Text(
                    text = model.searchingLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else if (noResults) {
                Text(
                    text = model.noResultsTemplate.format(model.submittedQuery),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                if (model.gameItems.isNotEmpty()) {
                    SectionHeader(model.gamesSectionLabel)
                    model.gameItems.forEach { item ->
                        ImageNameListItem(
                            model = item,
                            actionSink = actionSink,
                            modifier = Modifier,
                            padding = PaddingValues(),
                        )
                    }
                }
                if (model.songItems.isNotEmpty()) {
                    SectionHeader(model.songsSectionLabel)
                    model.songItems.forEach { item ->
                        ImageNameCaptionListItem(
                            model = item,
                            actionSink = actionSink,
                            modifier = Modifier,
                            padding = PaddingValues(),
                        )
                    }
                }
                if (model.artistItems.isNotEmpty()) {
                    SectionHeader(model.artistsSectionLabel)
                    model.artistItems.forEach { item ->
                        ImageNameListItem(
                            model = item,
                            actionSink = actionSink,
                            modifier = Modifier,
                            padding = PaddingValues(),
                        )
                    }
                }
            }
        }

        val topPaddingForSearchBar = topInsets.asPaddingValues().calculateTopPadding()

        SearchBar(
            model = model,
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

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
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
