package net.sigmabeta.chipbox.ui

interface ItemListView<VH : BaseViewHolder<*, *, *>> {
    fun onItemClick(position: Int)

    fun startDrag(holder: VH) = Unit
}