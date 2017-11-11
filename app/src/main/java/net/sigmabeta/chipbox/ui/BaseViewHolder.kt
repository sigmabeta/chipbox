package net.sigmabeta.chipbox.ui

import android.support.v7.widget.RecyclerView
import android.view.View
import net.sigmabeta.chipbox.model.domain.ListItem

abstract class BaseViewHolder<T : ListItem, VH : BaseViewHolder<T, VH, A>, A : BaseArrayAdapter<T, VH>>(
        val view: View,
        val adapter: A) : RecyclerView.ViewHolder(view), View.OnClickListener {
    init {
        view.setOnClickListener(this)
    }

    override fun onClick(clicked: View) {
        adapter.onItemClick(adapterPosition)
    }

    abstract fun bind(toBind: T)
}