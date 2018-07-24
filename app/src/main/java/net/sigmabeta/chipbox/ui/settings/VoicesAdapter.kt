package net.sigmabeta.chipbox.ui.settings

import android.view.View
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.audio.Voice
import net.sigmabeta.chipbox.ui.BaseArrayAdapter
import net.sigmabeta.chipbox.ui.ListView


class VoicesAdapter(view: ListView<Voice, VoiceViewHolder>) : BaseArrayAdapter<Voice, VoiceViewHolder>(view) {
    override fun getLayoutId() = R.layout.list_item_track_voice

    override fun createViewHolder(view: View): VoiceViewHolder {
        return VoiceViewHolder(view, this)
    }

    override fun bind(holder: VoiceViewHolder, item: Voice) {
        holder.bind(item)
    }
}