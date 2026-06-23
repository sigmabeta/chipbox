package net.sigmabeta.chipbox.common.ui.components.api

import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.ListModel

/**
 * Wraps any [ListModel] to make the row swipe-to-remove; its rendering counterpart is
 * [SwipeToRemoveBox], which reveals a remove affordance behind the row and dispatches
 * [dismissAction] once a right-to-left swipe is confirmed.
 *
 * Dismissibility is a view-state decision made when the list is built, not a property baked into
 * the domain model — so the same content model can be dismissable on one screen and static on
 * another. It composes with [DraggableListModel] by nesting: a draggable *and* dismissable row is a
 * [DismissibleListModel] wrapping a [DraggableListModel] (swipe outermost, drag handle within),
 * mirroring the rendered `SwipeToRemoveBox { DraggableListItem { … } }` structure.
 *
 * [dataId] and [columns] delegate to [content] so the reducer's re-emitted order and the screen's
 * live drag mirror agree on identity. [layoutId] *composes* the dismissable concept with the
 * content's own layout id, so wrapped rows still recycle per inner type yet never alias the bare,
 * swipe-less version of that type.
 */
data class DismissibleListModel(
    val content: ListModel,
    val dismissAction: SageAction,
) : ListModel() {
    override val dataId get() = content.dataId
    override val columns get() = content.columns

    override fun layoutId(): String = "$LAYOUT_ID_PREFIX${content.layoutId()}"

    private companion object {
        const val LAYOUT_ID_PREFIX = "Dismissible:"
    }
}
