package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import net.sigmabeta.chipbox.common.ui.components.api.subs.LabeledThingy
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.chipbox.strings.api.text
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.components.DropdownSettingListModel
import net.sigmabeta.sage.components.NameCaptionListModel
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.vector

/**
 * An expanding alternative to [LabelDropdownListItem] for a [DropdownSettingListModel].
 *
 * Collapsed, it reads like a [LabelValueListItem] — label on the left, the currently selected
 * value on the right — but with a down-caret trailing the value. Tapping it grows the row into a
 * [Column]: the same header (caret now flipped to point up) followed by one [NameCaptionListItem]
 * per option in [DropdownSettingListModel.settingsLabels]. Picking an option collapses the column
 * and dispatches [DropdownSettingListModel.onNewOptionSelected].
 *
 * For now the option rows are hard-coded to [NameCaptionListItem] with blank captions and only
 * render the [String] labels, matching [LabelDropdownListItem]'s capabilities. Later the column
 * will be able to host arbitrary [net.sigmabeta.sage.components.ListModel] rows.
 */
@Composable
fun ExpandingDropdownListItem(
    model: DropdownSettingListModel,
    actionSink: ActionSink,
    modifier: Modifier,
    padding: PaddingValues,
    defaultExpansion: Boolean = false,
) {
    var expanded by remember { mutableStateOf(defaultExpansion) }

    // Down-caret at rest (0f), flipped to point up (180f) when expanded; animate the flip.
    val caretRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "ExpandingDropdownListItem.caretRotation",
    )

    Column(modifier = modifier) {
        LabeledThingy(
            label = model.name,
            thingy = {
                TextValue(value = model.settingsLabels[model.selectedPosition])
                Icon(
                    imageVector = Icon.Caret.vector(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.rotate(caretRotation),
                )
            },
            onClick = { expanded = !expanded },
            onClickLabel = ChipboxStringId.ACCY_OCL_DROPDOWN.text(),
            modifier = Modifier,
            padding = padding,
        )

        AnimatedVisibility(visible = expanded) {
            Column {
                model.settingsLabels.forEachIndexed { index, label ->
                    NameCaptionListItem(
                        model = NameCaptionListModel(
                            dataId = index.toLong(),
                            name = label,
                            caption = "",
                            clickAction = model.onNewOptionSelected(index),
                            active = index == model.selectedPosition,
                        ),
                        // Collapse the column first, then forward the option's action upstream.
                        actionSink = { action ->
                            expanded = false
                            actionSink.sendAction(action)
                        },
                        modifier = Modifier,
                        padding = padding,
                    )
                }
            }
        }
    }
}
