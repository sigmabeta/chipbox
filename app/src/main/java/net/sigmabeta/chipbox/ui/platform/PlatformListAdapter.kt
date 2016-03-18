package net.sigmabeta.chipbox.ui.platform

import android.view.View
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.domain.Platform
import net.sigmabeta.chipbox.ui.BaseArrayAdapter
import net.sigmabeta.chipbox.ui.ItemListView

class PlatformListAdapter(view: ItemListView<PlatformViewHolder>) : BaseArrayAdapter<Platform, PlatformViewHolder>(view) {
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