package net.sigmabeta.chipbox.view.adapter

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.objects.Platform
import net.sigmabeta.chipbox.util.logError
import net.sigmabeta.chipbox.view.interfaces.PlatformListView
import net.sigmabeta.chipbox.view.viewholder.PlatformViewHolder
import java.util.*

class PlatformListAdapter(val view: PlatformListView) : BaseArrayAdapter() {
    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): RecyclerView.ViewHolder? {
        val row = LayoutInflater.from(parent?.context)
                ?.inflate(R.layout.list_item_platform, parent, false)

        if (row != null) {
            return PlatformViewHolder(row, this)
        } else {
            logError("[PlatformListAdapter] Unable to inflate row...")
            return null
        }
    }

    override fun bind(holder: RecyclerView.ViewHolder, dataset: ArrayList<*>, position: Int) {
        val platform = dataset.get(position) as Platform
        (holder as PlatformViewHolder).bind(platform)
    }

    fun onItemClick(id: Long, stringId: Int) {
        view.onItemClick(id, stringId)
    }
}