package net.sigmabeta.chipbox.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.ui.components.previews.PreviewActionSink
import net.sigmabeta.sage.ui.StringGenerator
import net.sigmabeta.chipbox.ui.components.previews.ChipboxPreview
import kotlinx.collections.immutable.toImmutableList
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.HorizontalScrollerListModel
import net.sigmabeta.sage.components.SmallTextListModel
import java.util.Random

@Preview
@Composable
private fun Light() {
    ChipboxPreview {
        Box(
            modifier = Modifier.background(
                color = MaterialTheme.colorScheme.background
            )
        ) {
            Sample()
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Dark() {
    ChipboxPreview {
        Box(
            modifier = Modifier.background(
                color = MaterialTheme.colorScheme.background
            )
        ) {
            Sample()
        }
    }
}

@Composable
@Suppress("MagicNumber", "LongMethod")
private fun Sample() {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        val padding = PaddingValues(horizontal = 16.dp)
        val actionSink = PreviewActionSink { }

        val seed = 1234L
        val random = Random(seed)
        val stringGenerator = StringGenerator(random)

        repeat(15) {
            val rowName = stringGenerator.generateTitle()

            SectionHeader(
                name = rowName,
                modifier = Modifier,
                padding = padding
            )

            HorizontalScroller(
                model = HorizontalScrollerListModel(
                    dataId = rowName.hashCode().toLong(),
                    scrollingItems = List(15) {
                        val name = stringGenerator.generateName()
                        SmallTextListModel(
                            name = name,
                            clickAction = SageAction.Noop,
                        )
                    }.toImmutableList()
                ),
                actionSink = actionSink,
                modifier = Modifier,
                showDebug = true,
                padding = padding
            )
        }
    }
}
