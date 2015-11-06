package net.sigmabeta.chipbox.view.adapter

import android.support.v7.widget.RecyclerView
import net.sigmabeta.chipbox.util.logError
import java.util.*

abstract class BaseArrayAdapter() : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var datasetValid = false
    var dataset: ArrayList<*>? = null

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (datasetValid) {
            val tempDataset = dataset

            if (tempDataset != null) {
                bind(holder, tempDataset, position)
            } else {
                logError("[BaseArrayAdapter] Can't bind view; dataset is not valid.")
            }
        } else {
            logError("[BaseArrayAdapter] Can't bind view; dataset is not valid.")
        }
    }

    override fun getItemCount(): Int {
        if (datasetValid) {
            return dataset?.size() ?: 0
        }

        logError("[BaseArrayAdapter] Dataset is not valid.")
        return 0
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    fun setData(data: ArrayList<*>) {
        dataset = data

        datasetValid = true
        notifyDataSetChanged()
    }

    abstract protected fun bind(holder: RecyclerView.ViewHolder, dataset: ArrayList<*>, position: Int)
}