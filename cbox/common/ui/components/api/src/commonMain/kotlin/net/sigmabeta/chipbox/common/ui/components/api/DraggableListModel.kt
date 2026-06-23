package net.sigmabeta.chipbox.common.ui.components.api

import net.sigmabeta.sage.components.ListModel

/**
 * Wraps any [ListModel] to mark it draggable in a ReorderableScreen; its rendering counterpart is
 * [DraggableListItem], which appends the drag handle around the wrapped content.
 *
 * Draggability is a view-state decision made when the list is built, not a property baked into the
 * domain model — so the same content model can be dragged on one screen and static on another, and
 * even models owned by `sage` can be made draggable from here without modifying them.
 *
 * Swipe-to-remove is a separate, composable concern: wrap this in a [DismissibleListModel] (swipe
 * outermost, drag handle within) for a row that both drags and dismisses.
 *
 * [dataId] and [columns] delegate to [content] so the reducer's re-emitted order and the screen's
 * live drag mirror agree on identity. [layoutId] *composes* the draggable concept with the
 * content's own layout id, so wrapped rows still recycle per inner type yet never alias the bare,
 * handle-less version of that type.
 */
data class DraggableListModel(
    val content: ListModel,
) : ListModel() {
    override val dataId get() = content.dataId
    override val columns get() = content.columns

    override fun layoutId(): String = "$LAYOUT_ID_PREFIX${content.layoutId()}"

    private companion object {
        const val LAYOUT_ID_PREFIX = "Draggable:"
    }
}
