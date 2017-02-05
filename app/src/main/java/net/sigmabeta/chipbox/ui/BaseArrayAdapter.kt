package net.sigmabeta.chipbox.ui

import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import net.sigmabeta.chipbox.model.domain.ListItem
import net.sigmabeta.chipbox.util.logError

abstract class BaseArrayAdapter<T : ListItem, VH : BaseViewHolder<*, *, *>>(val view: ItemListView<VH>) : RecyclerView.Adapter<VH>() {
    protected var datasetInternal: List<T>? = null

    var dataset: List<T>?
        get () {
            return null
        }
        set (value) {
            startAsyncListRefresh(value)
        }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): VH? {
        val item = LayoutInflater.from(parent?.context)?.inflate(getLayoutId(), parent, false)

        if (item != null) {
            return createViewHolder(item)
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
        return datasetInternal?.size ?: 0
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    open fun getItem(position: Int): T? {
        return datasetInternal?.get(position)
    }

    fun onItemClick(position: Int, clickedViewHolder: VH) {
        view.onItemClick(position, clickedViewHolder)
    }

    abstract fun getLayoutId(): Int

    abstract fun createViewHolder(view: View): VH

    abstract protected fun bind(holder: VH, item: T)

    private fun startAsyncListRefresh(input: List<T>?) {
        val callback = DiffCallback(datasetInternal, input)
        val result = DiffUtil.calculateDiff(callback)

        datasetInternal = input
        result.dispatchUpdatesTo(this@BaseArrayAdapter)
    }
}