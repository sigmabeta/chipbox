package net.sigmabeta.chipbox.player.emulators

/**
 * Hilt-injected list of every [Emulator] available in the current build variant. The
 * [net.sigmabeta.chipbox.player.generator.real.RealGenerator] iterates this list to find the
 * first one that supports a given track's file extension.
 *
 * Wrapped in a class (rather than a raw `List<Emulator>`) so Dagger/Hilt can distinguish the
 * binding from any other `List<Emulator>` that might show up in the graph.
 */
data class EmulatorProvider(val emulators: List<Emulator>)
