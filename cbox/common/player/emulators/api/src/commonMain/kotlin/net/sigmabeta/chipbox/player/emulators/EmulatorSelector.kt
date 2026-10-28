package net.sigmabeta.chipbox.player.emulators

/**
 * The single ordered emulator lookup shared by every playback path (the cached and uncached
 * [net.sigmabeta.chipbox.player.cache.PcmTrackSource.Factory]s and the A/B renderer).
 *
 * [select] preserves the order of [emulators], which is the DI order: dedicated chiptune backends
 * come first and vgmstream is the catch-all last. Probing through [Emulator.supportsExtension]
 * instead of the synchronous `isFileExtensionSupported` lets a backend complete an async setup
 * before answering — web vgmstream loads its WASM module on the first query. A shared extension
 * therefore short-circuits on its dedicated backend and never loads vgmstream; the first
 * vgmstream-only extension loads it once and then matches.
 *
 * A probe that throws (for example, a failed module load) propagates to the caller. Callers must
 * not turn that into "no emulator found": the real failure is what the playback error surface
 * needs.
 */
object EmulatorSelector {
    suspend fun select(emulators: List<Emulator>, extension: String): Emulator? =
        emulators.firstOrNull { it.supportsExtension(extension) }
}
