package net.sigmabeta.chipbox.ui

interface ItemListView<VH : BaseViewHolder<*, *, *>> {
    fun onItemClick(position: Int, clickedViewHolder: VH)

    fun startDrag(holder: VH) = Unit
}