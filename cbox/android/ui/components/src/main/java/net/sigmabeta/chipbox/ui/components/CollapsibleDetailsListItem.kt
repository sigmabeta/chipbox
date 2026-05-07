package net.sigmabeta.chipbox.ui.components

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.sage.components.CollapsibleDetailsListModel
import net.sigmabeta.chipbox.ui.components.previews.FullScreenOf
import net.sigmabeta.sage.ui.StringGenerator
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.vector
import java.util.Random
import kotlinx.collections.immutable.toImmutableList

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

            val icon = if (collapsed) Icon.PLUS else Icon.MINUS

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
            imageVector = Icon.FAVORITE_FILLED.vector(),
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
