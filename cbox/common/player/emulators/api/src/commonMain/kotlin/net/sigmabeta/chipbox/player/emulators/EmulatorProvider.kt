package net.sigmabeta.chipbox.player.emulators

/**
 * Injected list of every [Emulator] available in the current build variant, in selection order:
 * dedicated backends first, the broad catch-all (vgmstream) last. [EmulatorSelector] walks it to
 * find the backend for a track's file extension.
 *
 * Wrapped in a class (rather than a raw `List<Emulator>`) so Dagger/Hilt can distinguish the
 * binding from any other `List<Emulator>` that might show up in the graph.
 */
data class EmulatorProvider(val emulators: List<Emulator>)
