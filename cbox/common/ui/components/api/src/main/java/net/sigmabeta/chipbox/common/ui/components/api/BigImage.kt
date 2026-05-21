package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.common.ui.components.api.subs.CrossfadeImage
import net.sigmabeta.chipbox.common.ui.components.api.subs.ElevatedRoundRect
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.components.HeroImageListModel

@Composable
@Suppress("MagicNumber", "LongMethod", "UnsafeCallOnNullableType")
fun BigImage(
    model: HeroImageListModel,
    actionSink: ActionSink,
    modifier: Modifier,
    padding: PaddingValues,
) {
    ElevatedRoundRect(
        modifier = modifier
            .padding(padding)
            .heightIn(max = 384.dp)
            .fillMaxWidth()
            .clickable(onClick = { actionSink.sendAction(model.clickAction) }),
        cornerRadius = 16.dp,
    ) {
        CrossfadeImage(
            sourceInfo = model.sourceInfo,
            imagePlaceholder = model.imagePlaceholder,
            contentDescription = model.contentDescription,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
