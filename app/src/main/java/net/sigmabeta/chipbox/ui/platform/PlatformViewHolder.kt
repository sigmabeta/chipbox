package net.sigmabeta.chipbox.ui.platform

import android.view.View
import kotlinx.android.synthetic.main.list_item_platform.view.*
import net.sigmabeta.chipbox.model.domain.Platform
import net.sigmabeta.chipbox.ui.BaseViewHolder

class PlatformViewHolder(view: View, adapter: PlatformListAdapter) : BaseViewHolder<Platform, PlatformViewHolder, PlatformListAdapter>(view, adapter), View.OnClickListener {
    var platformId: Long? = null

    override fun getId(): Long? {
        return platformId
    }

    override fun bind(toBind: Platform) {
        platformId = toBind.id

        view.text_platform_name.setText(toBind.stringId)
    }
}
