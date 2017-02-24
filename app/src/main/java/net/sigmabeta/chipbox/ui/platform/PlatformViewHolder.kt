package net.sigmabeta.chipbox.ui.platform

import android.view.View
import kotlinx.android.synthetic.main.list_item_platform.view.*
import net.sigmabeta.chipbox.model.domain.Platform
import net.sigmabeta.chipbox.ui.BaseViewHolder

class PlatformViewHolder(view: View, adapter: PlatformListAdapter) : BaseViewHolder<Platform, PlatformViewHolder, PlatformListAdapter>(view, adapter) {
    override fun bind(toBind: Platform) {
        view.text_platform_name.setText(toBind.name)
    }
}
