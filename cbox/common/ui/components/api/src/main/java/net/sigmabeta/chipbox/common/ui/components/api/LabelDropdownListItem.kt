package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.sigmabeta.chipbox.common.ui.components.api.subs.Dropdown
import net.sigmabeta.chipbox.common.ui.components.api.subs.LabeledThingy
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.chipbox.strings.api.text
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.components.DropdownSettingListModel

@Composable
fun LabelDropdownListItem(
    model: DropdownSettingListModel,
    actionSink: ActionSink,
    defaultExpansion: Boolean = false,
    modifier: Modifier,
    padding: PaddingValues,
) {
    LabeledThingy(
        label = model.name,
        thingy = {
            Dropdown(
                defaultExpansion = defaultExpansion,
                selectedPosition = model.selectedPosition,
                settingsLabels = model.settingsLabels,
                onNewOptionSelected = { index -> actionSink.sendAction(model.onNewOptionSelected(index)) }
            )
        },
        onClick = {},
        onClickLabel = ChipboxStringId.ACCY_OCL_DROPDOWN.text(),
        modifier = modifier,
        padding = padding,
    )
}
