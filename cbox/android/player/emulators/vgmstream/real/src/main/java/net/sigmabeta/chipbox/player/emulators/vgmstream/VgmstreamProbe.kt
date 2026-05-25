package net.sigmabeta.chipbox.player.emulators.vgmstream

/** Metadata for one vgmstream subsong, gathered at scan time. */
data class VgmstreamSubsong(
    val subsong: Int, // 1-based; matches the value passed to VgmstreamEmulator.setTrackNumber
    val sampleRate: Int,
    val lengthMs: Long,
    val streamName: String,
)

/**
 * Scan-time bridge into the vgmstream native library: enumerates a file's subsongs and their
 * length/rate/name so the scanner can emit accurate tracks. Shares the native lib (and the
 * supported-extension set) with [VgmstreamEmulator]; uses the same loop/fade config so reported
 * lengths match playback.
 */
object VgmstreamProbe {
    /** Extensions vgmstream handles (minus generic wav/ogg/mp3), as a fast lookup set. */
    val supportedExtensions: Set<String> by lazy { VgmstreamEmulator.supportedFileExtensions.toSet() }

    fun isSupported(extension: String): Boolean = extension in supportedExtensions

    /**
     * Probes [path] (a real filesystem path). Returns one entry per subsong, or an empty list if
     * the file isn't a recognised vgmstream format.
     */
    fun probe(path: String): List<VgmstreamSubsong> {
        VgmstreamEmulator.loadNativeLib() // idempotent; ensures libvgmstream is available
        return probeInternal(path).mapIndexedNotNull { index, line ->
            if (line == null) return@mapIndexedNotNull null
            val parts = line.split('\t')
            if (parts.size < PROBE_FIELD_COUNT) return@mapIndexedNotNull null
            VgmstreamSubsong(
                subsong = index + 1,
                sampleRate = parts[0].toIntOrNull() ?: 0,
                lengthMs = parts[1].toLongOrNull() ?: 0L,
                streamName = parts[2],
            )
        }
    }

    private external fun probeInternal(path: String): Array<String?>

    // probeInternal lines are "sampleRate\tlengthMs\tstreamName".
    private const val PROBE_FIELD_COUNT = 3
}
