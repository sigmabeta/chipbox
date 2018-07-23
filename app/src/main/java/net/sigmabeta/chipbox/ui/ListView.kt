package net.sigmabeta.chipbox.ui

import io.realm.OrderedCollectionChangeSet
import net.sigmabeta.chipbox.model.domain.ListItem


interface ListView<in T: ListItem, in VH : BaseViewHolder<*, *, *>> : BaseView {
    fun setList(list: List<T>)

    fun animateChanges(changeset: OrderedCollectionChangeSet)

    fun onItemClick(position: Int)

    fun startDrag(holder: VH) = Unit

    fun isScrolledToBottom(): Boolean

    fun startRescan()

    fun refreshList()
}