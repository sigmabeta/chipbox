package net.sigmabeta.chipbox.backend.player

import net.sigmabeta.chipbox.model.audio.Voice
import net.sigmabeta.chipbox.util.external.getVoicesWrapper
import net.sigmabeta.chipbox.util.external.setTempoNative
import net.sigmabeta.chipbox.util.logInfo
import javax.inject.Inject

class Settings @Inject constructor() {
    var tempo: Int? = 100
        set (value: Int?) {
            if (value != null) {
                field = value
                logInfo("[Player] Setting tempo to $value")
                setTempoNative(value / 100.0)
            } else {
                field = 100
            }
        }

    var voices: MutableList<Voice>? = null
        get () {
            if (field == null) {
                field = getVoicesWrapper()
            }
            return field
        }

    fun onTrackChange() {

    }
}
