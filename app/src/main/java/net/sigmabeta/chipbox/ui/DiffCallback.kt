package net.sigmabeta.chipbox.ui

import androidx.recyclerview.widget.DiffUtil
import net.sigmabeta.chipbox.model.domain.ListItem

class DiffCallback<out T : ListItem>(val oldList: List<T>?,
                                     val newList: List<T>?) : DiffUtil.Callback() {
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = getOldItem(oldItemPosition)
        val newItem = getNewItem(newItemPosition)

        return oldItem?.isTheSameAs(newItem) ?: false
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = getOldItem(oldItemPosition)
        val newItem = getNewItem(newItemPosition)

        return oldItem?.hasSameContentAs(newItem) ?: false
    }

    override fun getOldListSize() = oldList?.size ?: 0

    override fun getNewListSize() = newList?.size ?: 0

    /**
     * Private Methods
     */

    private fun getOldItem(position: Int) = oldList?.get(position)

    private fun getNewItem(position: Int) = newList?.get(position)
}