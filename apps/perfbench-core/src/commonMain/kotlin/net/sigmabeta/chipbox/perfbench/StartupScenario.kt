package net.sigmabeta.chipbox.perfbench

/**
 * The canonical startup scenarios the device benchmark measures.
 *
 * The [id] is the scenario key stored in a [StartupResult] and matched by a baseline diff; it is
 * stable across runs and devices. Both a cold launch (no process) and a warm launch (new activity
 * in a live process) report time-to-first-content.
 */
enum class StartupScenario(val id: String) {
    COLD("startup.cold"),
    WARM("startup.warm"),
    ;

    companion object {
        /** Every scenario, in the order the harness reports them. */
        val all: List<StartupScenario> = entries.toList()

        /** Resolves an [id] such as `startup.cold`, or `null` if it names no scenario. */
        fun fromId(id: String): StartupScenario? = entries.firstOrNull { it.id == id }
    }
}
