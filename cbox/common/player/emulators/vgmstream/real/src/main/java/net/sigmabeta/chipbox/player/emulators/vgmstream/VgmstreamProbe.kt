package net.sigmabeta.chipbox.player.emulators.vgmstream

import java.io.File

/**
 * Native [VgmstreamProber] implementation: enumerates a file's subsongs and their length/rate/name
 * so the scanner can emit accurate tracks. Shares the native lib (and the supported-extension set)
 * with [VgmstreamEmulator]; uses the same loop/fade config so reported lengths match playback.
 *
 * vgmstream decodes by filesystem path, so [probe] stages the bytes to a temp file first — keeping
 * all filesystem/JNI work behind the commonMain [VgmstreamProber] interface the scanner depends on.
 */
object VgmstreamProbe : VgmstreamProber {
    /** Extensions vgmstream handles (minus generic wav/ogg/mp3), as a fast lookup set. */
    private val supportedExtensions: Set<String> by lazy { VgmstreamEmulator.supportedFileExtensions.toSet() }

    override fun isSupported(extension: String): Boolean = extension in supportedExtensions

    override fun probe(bytes: ByteArray, extension: String): List<VgmstreamSubsong> {
        VgmstreamEmulator.loadNativeLib() // idempotent; ensures libvgmstream is available
        val temp = File.createTempFile("vgmprobe_", ".$extension")
        return try {
            temp.writeBytes(bytes)
            probeInternal(temp.absolutePath).mapIndexedNotNull { index, line ->
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
        } finally {
            temp.delete()
        }
    }

    private external fun probeInternal(path: String): Array<String?>

    // probeInternal lines are "sampleRate\tlengthMs\tstreamName".
    private const val PROBE_FIELD_COUNT = 3
}
