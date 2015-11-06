package net.sigmabeta.chipbox.view.viewholder

import android.support.v7.widget.RecyclerView
import android.view.View
import kotlinx.android.synthetic.list_item_platform.view.text_platform_name
import net.sigmabeta.chipbox.model.objects.Platform
import net.sigmabeta.chipbox.view.adapter.PlatformListAdapter

class PlatformViewHolder(val view: View, val adapter: PlatformListAdapter) : RecyclerView.ViewHolder(view), View.OnClickListener {
    var platform: Platform? = null

    init {
        view.setOnClickListener(this)
    }

    fun bind(toBind: Platform) {
        platform = toBind

        view.text_platform_name.setText(toBind.stringId)
    }


    override fun onClick(v: View) {
        adapter.onItemClick(platform?.id ?: return)
    }
}
