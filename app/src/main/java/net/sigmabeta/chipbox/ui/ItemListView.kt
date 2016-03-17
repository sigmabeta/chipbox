package net.sigmabeta.chipbox.ui

interface ItemListView<VH : BaseViewHolder<*, *, *>> {
    fun onItemClick(id: Long, clickedViewHolder: VH)
}