package net.sigmabeta.chipbox.player.director

enum class PlayerState {
    IDLE,               // Nothing has happened yet
    STOPPED,            // Playback existed, but stopped
    BUFFERING,          // No audio is playing, generator trying to build a buffer
    PRELOADING,         // Audio is playing, generator trying to preload next track
    PLAYING,            // Audio is playing
    FAST_FORWARDING,    // Seeking forward. Should be fast
    REWINDING,          // Seeking backward. Usually takes longer
    PAUSED,             // Audio is temporarily not playing; generator can still buffer
    ENDING,             // Audio still playing, but no more tracks to give to generator
    ERROR               // What!?
}