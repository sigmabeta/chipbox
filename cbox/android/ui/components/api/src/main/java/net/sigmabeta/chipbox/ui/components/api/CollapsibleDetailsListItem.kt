package net.sigmabeta.chipbox.ui.components.api

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.sigmabeta.sage.components.CollapsibleDetailsListModel
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.vector
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun CollapsibleDetailsListItem(
    model: CollapsibleDetailsListModel,
    padding: PaddingValues,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        var collapsed by remember { mutableStateOf(model.initiallyCollapsed) }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .clickable { collapsed = !collapsed }
                .padding(padding),
        ) {
            Text(
                text = model.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .weight(1.0f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            val icon = if (collapsed) Icon.Plus else Icon.Minus

            Icon(
                imageVector = icon.vector(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }

        val enterTransition = enterTransition()
        val exitTransition = exitTransition()

        model.detailItems.forEach { item ->
            AnimatedVisibility(
                visible = !collapsed,
                enter = enterTransition,
                exit = exitTransition,
            ) {
                DetailItem(
                    detailText = item,
                    padding = padding,
                )
            }
        }
    }
}

@Composable
private fun enterTransition() = fadeIn(
    animationSpec = tween(delayMillis = 400)
)

@Composable
private fun exitTransition() = fadeOut() + shrinkVertically(
    animationSpec = tween(delayMillis = 100)
)

@Composable
@Suppress("MagicNumber")
private fun DetailItem(
    detailText: String,
    padding: PaddingValues,
) {
    Row(
        modifier = Modifier
            .padding(padding),
    ) {
        Icon(
            imageVector = Icon.FavoriteFilled.vector(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .padding(end = 16.dp)
                .size(20.dp)
                .alpha(0.7f)
        )

        Text(
            text = detailText,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .weight(1f),
        )
    }
}
