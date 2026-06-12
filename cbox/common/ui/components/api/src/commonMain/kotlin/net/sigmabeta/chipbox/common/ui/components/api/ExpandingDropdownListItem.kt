package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.common.ui.components.api.subs.LabeledThingy
import net.sigmabeta.chipbox.common.ui.components.api.utils.FocusAreaShape
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.chipbox.strings.api.text
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.components.DropdownSettingListModel
import net.sigmabeta.sage.ui.Icon as SageIcon
import net.sigmabeta.sage.ui.vector

// When expanded the row lifts off the list: a drop shadow + tonal elevation, a little breathing
// room around it, and interior padding. The card itself only pads the content's top; the sides and
// bottom are carried by the header ("previous selection") row and the options Surface, so the
// options' surfaceContainer background runs all the way to the card edges.
private val DROPDOWN_EXPANDED_ELEVATION = 4.dp
private val DROPDOWN_EXPANDED_PADDING = 16.dp
private val DROPDOWN_EXPANDED_CONTENT_PADDING = 4.dp

/**
 * The renderer for a [DropdownSettingListModel].
 *
 * Collapsed, it reads like a [LabelValueListItem] — the setting [DropdownSettingListModel.name] on
 * the left, the selected option's label inline on the right — with a trailing down-caret. Tapping
 * the header dispatches [DropdownSettingListModel.onExpandClicked]; the screen toggles its tracked
 * expanded dropdown, which flows back as [DropdownSettingListModel.expanded] and flips the caret up
 * (animated), reveals the options, and lifts the whole row with an animated elevation + padding
 * increase. Each expanded row is an arbitrary [net.sigmabeta.sage.components.ListModel], rendered
 * through its own renderer via [Content], so an option can be plain text, a captioned row, an icon
 * row, an image, and so on. Picking an option dispatches that option's own `clickAction` (the
 * screen collapses in response).
 *
 * This renderer holds no expansion state of its own — it is fully driven by [model].
 */
@Composable
fun ExpandingDropdownListItem(
    model: DropdownSettingListModel,
    actionSink: ActionSink,
    modifier: Modifier,
    padding: PaddingValues,
) {
    // Down-caret at rest (0f), flipped to point up (180f) when expanded; animate the flip.
    val caretRotation by animateFloatAsState(
        targetValue = if (model.expanded) 180f else 0f,
        label = "ExpandingDropdownListItem.caretRotation",
    )
    val elevation by animateDpAsState(
        targetValue = if (model.expanded) DROPDOWN_EXPANDED_ELEVATION else 0.dp,
        label = "ExpandingDropdownListItem.elevation",
    )
    val liftPadding by animateDpAsState(
        targetValue = if (model.expanded) DROPDOWN_EXPANDED_PADDING else 0.dp,
        label = "ExpandingDropdownListItem.liftPadding",
    )
    val contentPadding by animateDpAsState(
        targetValue = if (model.expanded) DROPDOWN_EXPANDED_CONTENT_PADDING else 0.dp,
        label = "ExpandingDropdownListItem.contentPadding",
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = FocusAreaShape,
        shadowElevation = elevation,
        tonalElevation = elevation,
        modifier = modifier.padding(liftPadding),
    ) {
        Column(modifier = Modifier.padding(top = contentPadding)) {
            LabeledThingy(
                label = model.name,
                thingy = {
                    TextValue(value = model.options[model.selectedPosition].first)
                    Icon(
                        imageVector = SageIcon.Caret.vector(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.rotate(caretRotation),
                    )
                },
                onClick = { actionSink.sendAction(model.onExpandClicked) },
                onClickLabel = ChipboxStringId.ACCY_OCL_DROPDOWN.text(),
                modifier = Modifier.padding(horizontal = contentPadding),
                padding = padding,
            )

            AnimatedVisibility(visible = model.expanded) {
                // The options sit on a distinct surfaceContainer fill that runs to the card edges
                // (plain `surface` equals `background` in the Chipbox scheme, so it wouldn't show);
                // the Surface's own interior padding (sides + bottom) keeps the options off those
                // edges.
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(
                        modifier = Modifier.padding(
                            start = contentPadding,
                            end = contentPadding,
                            bottom = contentPadding,
                        ),
                    ) {
                        model.options.forEach { (_, option) ->
                            option.Content(
                                sink = actionSink,
                                debug = false,
                                mod = Modifier,
                                pad = padding,
                            )
                        }
                    }
                }
            }
        }
    }
}
