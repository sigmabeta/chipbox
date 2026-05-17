package net.sigmabeta.chipbox.features.search.real

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.sigmabeta.sage.appcomm.ActionSink

@Composable
@Suppress("LongMethod")
internal fun SearchContent(
    model: SearchModel,
    query: String,
    textFieldUpdater: (String) -> Unit,
    actionSink: ActionSink,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        val enoughToGetBelowSearchBar: Dp = 96.dp
        val topInsets = WindowInsets.statusBars
        val sidePadding = WindowInsets(left = 16.dp, right = 16.dp)

        val contentPadding: PaddingValues = WindowInsets(top = enoughToGetBelowSearchBar)
            .add(topInsets)
            .add(sidePadding)
            .asPaddingValues()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        ) {
            if (query.isBlank()) {
                Text(
                    text = model.emptyPrompt,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                model.fakeRecentSearches.forEach { label ->
                    FakeRecentRow(label)
                }
            } else {
                Text(
                    text = model.comingSoonTemplate.format(query),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }

        val topPaddingForSearchBar = topInsets.asPaddingValues().calculateTopPadding()

        SearchBar(
            text = query,
            model = model,
            textFieldUpdater = textFieldUpdater,
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

/**
 * A stubbed "recent search" row. Intentionally non-interactive and backed by fake data —
 * this is the visual seam for real search history later.
 */
@Composable
private fun FakeRecentRow(label: String) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
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
