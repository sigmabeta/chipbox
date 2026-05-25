package net.sigmabeta.chipbox.player.emulators.vgmstream

/** Metadata for one vgmstream subsong, gathered at scan time. */
data class VgmstreamSubsong(
    val subsong: Int, // 1-based; matches the value passed to VgmstreamEmulator.setTrackNumber
    val sampleRate: Int,
    val lengthMs: Long,
    val streamName: String,
)

/**
 * Scan-time bridge into the vgmstream native library. Like [net.sigmabeta.chipbox.player.emulators.Emulator],
 * the contract lives in commonMain and the native implementation (player/emulators/vgmstream/real)
 * is injected via DI, so the scanner stays platform-agnostic (and JS-enforceable). The impl stages
 * the file's [bytes] to a real path — vgmstream decodes by filesystem path — and enumerates subsongs.
 */
interface VgmstreamProber {
    /** Whether vgmstream handles [extension] (lowercase, without leading dot). */
    fun isSupported(extension: String): Boolean

    /** Probe [bytes] (a file with the given [extension]). One entry per subsong, empty if unrecognised. */
    fun probe(bytes: ByteArray, extension: String): List<VgmstreamSubsong>
}
