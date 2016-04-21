package net.sigmabeta.chipbox.model.audio

import net.sigmabeta.chipbox.util.external.muteVoiceNative

class Voice(val position: Int, val name: String) {
    var enabled: Boolean = true
        set (value) {
            field = value
            muteVoiceNative(position, if (field) 0 else 1)
        }
}