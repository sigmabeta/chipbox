package net.sigmabeta.chipbox.ui

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import net.sigmabeta.chipbox.util.logError

abstract class BaseArrayAdapter<T, VH : BaseViewHolder<*, *, *>>(val view: ItemListView<VH>) : RecyclerView.Adapter<VH>() {
    var dataset: List<T>? = null
        set (value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): VH? {
        if (viewType == TYPE_HEADER) {
            val headerView = LayoutInflater.from(parent?.context)?.inflate(getHeaderLayoutId(), parent, false)

            if (headerView != null) {
                return createHeaderViewHolder(headerView)
            } else {
                logError("[BaseArrayAdapter] Unable to inflate view...")
                return null
            }
        } else {
            val itemView = LayoutInflater.from(parent?.context)?.inflate(getLayoutId(), parent, false)

            if (itemView != null) {
                return createViewHolder(itemView)
            } else {
                logError("[BaseArrayAdapter] Unable to inflate view...")
                return null
            }
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

    fun getItem(position: Int): T? {
        if (getHeaderLayoutId() != 0) {
            if (position > 0) {
                return dataset?.get(position - 1)
            } else {
                return null
            }
        } else {
            return dataset?.get(position)
        }
    }

    fun onItemClick(id: Long, clickedViewHolder: VH) {
        view.onItemClick(id, clickedViewHolder)
    }

    override fun getItemViewType(position: Int): Int {
        return if (getHeaderLayoutId() != 0 && position == 0) {
            TYPE_HEADER
        } else {
            TYPE_ITEM
        }
    }

    open fun getHeaderLayoutId(): Int {
        return 0
    }

    open fun createHeaderViewHolder(view: View): VH? {
        return null
    }

    abstract fun getLayoutId(): Int

    abstract fun createViewHolder(view: View): VH

    abstract protected fun bind(holder: VH, item: T)

    companion object {
        val TYPE_HEADER = 0
        val TYPE_ITEM = 1
    }
}