package net.sigmabeta.chipbox.ui

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import net.sigmabeta.chipbox.util.logError

abstract class BaseArrayAdapter<T, VH : BaseViewHolder<*, *, *>>(val view: ItemListView<VH>) : RecyclerView.Adapter<VH>() {
    var dataset: MutableList<T>? = null
        set (value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): VH? {
        val card = LayoutInflater.from(parent?.context)?.inflate(getLayoutId(), parent, false)

        if (card != null) {
            return createViewHolder(card)
        } else {
            logError("[BaseArrayAdapter] Unable to inflate view...")
            return null
        }
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        getItem(position)?.let {
            bind(holder, it)
        } ?: let {
            logError("[BaseArrayAdapter] Can't bind view; dataset is not valid.")
        }
    }

    override fun getItemCount(): Int {
        return dataset?.size ?: 0
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    open fun getItem(position: Int): T? {
        return dataset?.get(position)
    }

    fun onItemClick(id: Long, clickedViewHolder: VH) {
        view.onItemClick(id, clickedViewHolder)
    }

    abstract fun getLayoutId(): Int

    abstract fun createViewHolder(view: View): VH

    abstract protected fun bind(holder: VH, item: T)
}