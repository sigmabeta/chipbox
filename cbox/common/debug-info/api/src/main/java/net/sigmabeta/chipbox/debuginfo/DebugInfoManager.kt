package net.sigmabeta.chipbox.debuginfo

import kotlinx.coroutines.flow.StateFlow

/**
 * Primary data source for the debug PlaybackStatus screen. Collects the diagnostic
 * `StateFlow`s exposed by the player components (generator, speaker, buffer manager) plus
 * the Director's track / playback / session streams, and exposes them as one combined
 * [PlaybackDebugInfo] snapshot.
 */
interface DebugInfoManager {
    fun debugInfo(): StateFlow<PlaybackDebugInfo>
}
