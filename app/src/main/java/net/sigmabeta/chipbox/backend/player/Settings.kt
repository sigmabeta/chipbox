package net.sigmabeta.chipbox.backend.player

import net.sigmabeta.chipbox.model.audio.Voice
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Settings @Inject constructor() {
    var tempo: Int? = 100
        set (value: Int?) {
            if (value != null) {
                field = value
                Timber.i("Setting tempo to %s", value)
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
