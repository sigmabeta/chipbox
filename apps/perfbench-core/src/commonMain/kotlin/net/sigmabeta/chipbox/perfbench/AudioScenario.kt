package net.sigmabeta.chipbox.perfbench

/**
 * The canonical audio scenarios (v1 subset) and the file extensions each one renders.
 *
 * The [id] is the scenario key stored in an [AudioResult] and matched by a baseline diff; it is
 * stable across runs and machines. The extension set is what selects a scenario's representative
 * track from the corpus, so it mirrors the corresponding emulator backend's supported extensions.
 */
enum class AudioScenario(val id: String, val extensions: Set<String>) {
    USF("audio.usf", setOf("usf", "miniusf")),
    VGM("audio.vgm", setOf("vgm", "vgz")),
    PSF("audio.psf", setOf("psf", "minipsf", "psf2", "minipsf2")),
    ;

    companion object {
        /** Every scenario, in the order the harness reports them. */
        val all: List<AudioScenario> = entries.toList()

        /** Resolves an [id] such as `audio.usf`, or `null` if it names no known scenario. */
        fun fromId(id: String): AudioScenario? = entries.firstOrNull { it.id == id }
    }
}
