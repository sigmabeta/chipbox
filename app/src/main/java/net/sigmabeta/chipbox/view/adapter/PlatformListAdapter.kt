package net.sigmabeta.chipbox.view.adapter

import android.view.View
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.objects.Platform
import net.sigmabeta.chipbox.view.interfaces.ItemListView
import net.sigmabeta.chipbox.view.viewholder.PlatformViewHolder

class PlatformListAdapter(view: ItemListView) : BaseArrayAdapter<Platform, PlatformViewHolder>(view) {
    override fun getLayoutId(): Int {
        return R.layout.list_item_platform
    }

    override fun createViewHolder(view: View): PlatformViewHolder {
        return PlatformViewHolder(view, this)
    }

    override fun bind(holder: PlatformViewHolder, item: Platform) {
        holder.bind(item)
    }
}