package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.components.SquareItemListModel
import net.sigmabeta.sage.components.SubsectionListModel
import net.sigmabeta.sage.components.WideItemListModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Subsection(
    model: SubsectionListModel,
    actionSink: ActionSink,
    modifier: Modifier,
    padding: PaddingValues,
) {
    val maxItemsInEachRow = 2
    FlowRow(
        maxItemsInEachRow = maxItemsInEachRow,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .padding(8.dp)
            .wrapContentHeight()
            .clip(SubsectionShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(8.dp)
    ) {
        SubsectionHeader(
            model = model.titleModel,
            modifier = Modifier
        )

        model.children.forEach {
            when (it) {
                is WideItemListModel -> WideItem(
                    model = it,
                    actionSink = actionSink,
                    modifier = Modifier.weight(1.0f),
                    padding = PaddingValues()
                )

                is SquareItemListModel -> SquareItem(
                    model = it,
                    actionSink = actionSink,
                    modifier = Modifier,
                    padding = padding,
                )

                else -> TODO()
            }
        }

        val spacerCount = model.children.size % maxItemsInEachRow

        for (i in 0..spacerCount) {
            Spacer(Modifier.weight(1.0f))
        }
    }
}

private val SubsectionShape = RoundedCornerShape(16.dp)
