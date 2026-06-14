package net.sigmabeta.chipbox.debug

/**
 * Which [Speaker][net.sigmabeta.chipbox.player.speaker.Speaker] implementation the player uses —
 * a debug-menu switch.
 *
 * [REAL] plays to the device's audio output; [FILE] writes the PCM to a WAV file; [TEXT] prints
 * each audio buffer as a frame table to the log (no audio output).
 */
enum class SpeakerSource {
    REAL,
    FILE,
    TEXT,
    ;

    companion object {
        val DEFAULT: SpeakerSource = REAL

        fun fromStorageValue(value: String?): SpeakerSource =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
