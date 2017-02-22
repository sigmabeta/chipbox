package net.sigmabeta.chipbox.backend.player

import net.sigmabeta.chipbox.model.audio.Voice
import net.sigmabeta.chipbox.util.logInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Settings @Inject constructor() {
    var tempo: Int? = 100
        set (value: Int?) {
            if (value != null) {
                field = value
                logInfo("[Player] Setting tempo to $value")
            } else {
                field = 100
            }
        }

    var voices: MutableList<Voice>? = null

    fun onTrackChange() {
        tempo = null
        voices = null
    }
}
