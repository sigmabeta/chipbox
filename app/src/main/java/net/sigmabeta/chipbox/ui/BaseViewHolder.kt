package net.sigmabeta.chipbox.ui

import android.support.v7.widget.RecyclerView
import android.view.View
import kotlinx.android.extensions.LayoutContainer
import net.sigmabeta.chipbox.model.domain.ListItem

abstract class BaseViewHolder<T : ListItem, VH : BaseViewHolder<T, VH, A>, A : BaseArrayAdapter<T, VH>>(
        override val containerView: View,
        val adapter: A) : RecyclerView.ViewHolder(containerView), View.OnClickListener, LayoutContainer {
    init {
        containerView.setOnClickListener(this)
    }

    override fun onClick(clicked: View) {
        adapter.onItemClick(adapterPosition)
    }

    abstract fun bind(toBind: T)
}