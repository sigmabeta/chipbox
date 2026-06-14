package net.sigmabeta.chipbox.debug

/**
 * Which [Generator][net.sigmabeta.chipbox.player.generator.Generator] implementation the player
 * uses — a debug-menu switch.
 *
 * [REAL] emulates the actual sound chips; [FAKE] is an in-process procedural synth (no real
 * emulation) — useful for isolating audio-pipeline issues from emulator behaviour.
 */
enum class GeneratorSource {
    REAL,
    FAKE,
    ;

    companion object {
        val DEFAULT: GeneratorSource = REAL

        fun fromStorageValue(value: String?): GeneratorSource =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
