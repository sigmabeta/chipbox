package net.sigmabeta.chipbox.ui.settings

import net.sigmabeta.chipbox.model.audio.Voice
import net.sigmabeta.chipbox.ui.ListView

interface SettingsView : ListView<Voice, VoiceViewHolder> {
    fun notifyChanged(position: Int)

    fun setDropdownValue(index: Int)

}