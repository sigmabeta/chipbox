package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.common.ui.components.api.utils.FocusAreaShape
import net.sigmabeta.chipbox.common.ui.components.api.utils.innerFocusPadding
import net.sigmabeta.chipbox.common.ui.components.api.utils.outerFocusPadding
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.chipbox.strings.api.text
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.components.NameCaptionCheckboxListModel

@Composable
fun NameCaptionCheckboxListItem(
    model: NameCaptionCheckboxListModel,
    actionSink: ActionSink,
    modifier: Modifier,
    padding: PaddingValues,
) {
    val accyStateDescription = when (model.checked) {
        true -> ChipboxStringId.ACCY_ST_DESC_CHECKED.text()
        false -> ChipboxStringId.ACCY_ST_DESC_UNCHECKED.text()
        null -> ChipboxStringId.ACCY_ST_DESC_LOADING.text()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(padding.outerFocusPadding())
            .fillMaxWidth()
            .clip(FocusAreaShape)
            .clickable(
                onClick = { actionSink.sendAction(model.clickAction) },
                onClickLabel = ChipboxStringId.ACCY_OCL_CHECKBOX.text(),
            )
            .padding(padding.innerFocusPadding())
            // The whole row carries the checked/unchecked/loading state; the inner Checkbox clears
            // its own semantics so a screen reader announces this once, not twice.
            .semantics { stateDescription = accyStateDescription },
    ) {
        Column(
            modifier = Modifier.weight(1.0f)
        ) {
            CrossfadeText(
                text = model.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textModifier = Modifier
                    .fillMaxWidth()
                    .paddingFromBaseline(top = 24.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            CrossfadeText(
                text = model.caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textModifier = Modifier
                    .fillMaxWidth()
                    .paddingFromBaseline(bottom = 12.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Crossfade(
            targetState = model.checked,
            label = "CheckboxState"
        ) { checked ->
            when {
                checked != null -> Checkbox(
                    checked = checked,
                    onCheckedChange = { actionSink.sendAction(model.clickAction) },
                    Modifier.clearAndSetSemantics { }
                )

                else -> CircularProgressIndicator(
                    modifier = Modifier
                        .padding(12.dp)
                        .size(24.dp)
                )
            }
        }
    }
}
