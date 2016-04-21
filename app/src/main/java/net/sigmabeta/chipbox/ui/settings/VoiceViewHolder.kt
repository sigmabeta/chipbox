package net.sigmabeta.chipbox.ui.settings

import android.view.View
import kotlinx.android.synthetic.main.list_item_track_voice.view.*
import net.sigmabeta.chipbox.model.audio.Voice
import net.sigmabeta.chipbox.ui.BaseViewHolder

class VoiceViewHolder(view: View, adapter: VoicesAdapter) : BaseViewHolder<Voice, VoiceViewHolder, VoicesAdapter>(view, adapter) {
    init {
        view.checkbox_enabled.setOnClickListener(this)
    }

    override fun bind(toBind: Voice) {
        view.text_voice_name.text = toBind.name
        view.checkbox_enabled.isChecked = toBind.enabled
    }

    override fun getId() = adapterPosition.toLong()
}