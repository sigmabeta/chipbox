package net.sigmabeta.chipbox.notif

/**
 * App-lifetime observer that turns scanner failures into error notifications. Apps call [observe]
 * once at startup, mirroring `PlaybackHistoryRecorder.observe()`.
 */
interface ScanFailureNotifRecorder {
    fun observe()
}
