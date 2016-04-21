package net.sigmabeta.chipbox.ui.settings

import net.sigmabeta.chipbox.model.audio.Voice
import net.sigmabeta.chipbox.ui.BaseView

interface SettingsView : BaseView {
    fun notifyChanged(position: Int)

    fun setVoices(voices: MutableList<Voice>?)

    fun setDropdownValue(index: Int)

}