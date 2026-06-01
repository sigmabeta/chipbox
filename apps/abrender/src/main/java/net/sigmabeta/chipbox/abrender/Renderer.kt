package net.sigmabeta.chipbox.abrender

import java.io.File
import net.sigmabeta.chipbox.player.common.EbuR128
import net.sigmabeta.chipbox.player.emulators.Emulator
import net.sigmabeta.chipbox.player.emulators.gba.GbaEmulator
import net.sigmabeta.chipbox.player.emulators.gme.GmeEmulator
import net.sigmabeta.chipbox.player.emulators.ncsf.NcsfEmulator
import net.sigmabeta.chipbox.player.emulators.psf.PsfEmulator
import net.sigmabeta.chipbox.player.emulators.ssf.SsfEmulator
import net.sigmabeta.chipbox.player.emulators.twosf.TwosfEmulator
import net.sigmabeta.chipbox.player.emulators.usf.UsfEmulator
import net.sigmabeta.chipbox.player.emulators.vgm.VgmEmulator
import net.sigmabeta.chipbox.player.emulators.vgmstream.VgmstreamEmulator

private const val BUFFER_FRAMES = 4096
private const val SHORTS_PER_FRAME = 2

/** A track resolved from the library, flattened to exactly what a render needs. */
data class SelectedTrack(
    val trackKey: String,
    val path: String,
    val trackNumber: Int,
    val extension: String,
    val game: String,
    val title: String,
)

/**
 * Owns the singleton emulator backends and renders one track at a time. Every backend is an `object`
 * with process-lifetime native state, so this class is single-threaded by construction: one track is
 * loaded, drained, and torn down before the next.
 *
 * Rendering deliberately bypasses [Emulator.loadTrack]/[Emulator.generateBuffer] and calls the
 * `*Internal` native entry points directly. That skips the base class's declared-length frame budget
 * and fade handling: for A/B we want a fixed, deterministic wall of raw samples (default 30 s),
 * identical across builds, not whatever length the track metadata declares.
 */
class EmulatorBank {
    private val emulators: List<Emulator> = listOf(
        TwosfEmulator, GbaEmulator, GmeEmulator, NcsfEmulator,
        PsfEmulator, SsfEmulator, VgmEmulator, UsfEmulator, VgmstreamEmulator,
    )
    private val nativeLoaded = HashSet<Emulator>()

    /** Extensions any backend can decode — used to skip unsupported tracks before rendering. */
    val supportedExtensions: Set<String> =
        emulators.flatMap { it.supportedFileExtensions }.map { it.lowercase() }.toSet()

    fun emulatorFor(extension: String): Emulator? =
        emulators.firstOrNull { it.isFileExtensionSupported(extension.lowercase()) }

    /**
     * Renders [seconds] of [track] to [wavFile] and returns its measurement. Never throws for a
     * per-track failure (bad file, native error, time cap) — the failure is captured in
     * [TrackMetrics.error] so an unattended 400-game run keeps going.
     *
     * [maxWallMillis] guards against a backend that produces audio forever-but-slowly; it's checked
     * between buffers. A single native call that wedges entirely is not interruptible here.
     */
    @Suppress("ReturnCount") // guard clauses for each distinct load failure read clearer than nesting
    fun render(track: SelectedTrack, seconds: Int, wavFile: File, maxWallMillis: Long): TrackMetrics {
        val emulator = emulatorFor(track.extension)
            ?: return failure(track, wavFile.name, "no emulator for .${track.extension}")

        if (nativeLoaded.add(emulator)) emulator.loadNativeLib()

        var loaded = false
        return try {
            emulator.setTrackNumber(track.trackNumber)
            emulator.loadTrackInternal(track.path)
            loaded = true

            val loadError = emulator.getLastError()
            if (loadError != null) return failure(track, wavFile.name, loadError)

            val sampleRate = emulator.getSampleRateInternal()
            if (sampleRate <= 0) return failure(track, wavFile.name, "invalid sample rate $sampleRate")

            renderLoop(emulator, track, seconds, sampleRate, wavFile, maxWallMillis)
        } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
            // A bad track must never abort an unattended 400-game run; record and move on.
            failure(track, wavFile.name, "exception: ${t.message ?: t.javaClass.simpleName}")
        } finally {
            if (loaded) runCatching { emulator.teardownInternal() }
        }
    }

    @Suppress("LongParameterList")
    private fun renderLoop(
        emulator: Emulator,
        track: SelectedTrack,
        seconds: Int,
        sampleRate: Int,
        wavFile: File,
        maxWallMillis: Long,
    ): TrackMetrics {
        val targetFrames = seconds.toLong() * sampleRate
        val stats = SignalStats()
        val loudness = EbuR128(sampleRate)
        val buffer = ShortArray(BUFFER_FRAMES * SHORTS_PER_FRAME)
        val wav = WavWriter(wavFile, sampleRate)
        val deadline = System.currentTimeMillis() + maxWallMillis

        var produced = 0L
        var endedEarly = false // track stopped producing before the target — a natural end, not an error
        try {
            // Deadline is part of the loop condition so the body needs a single break (the early-end).
            while (produced < targetFrames && System.currentTimeMillis() <= deadline) {
                val want = minOf(BUFFER_FRAMES.toLong(), targetFrames - produced).toInt()
                val frames = emulator.generateBufferInternal(buffer, want)
                if (frames <= 0) {
                    endedEarly = true
                    break
                }
                stats.process(buffer, frames)
                loudness.process(buffer, frames)
                wav.write(buffer, frames)
                produced += frames
            }
        } finally {
            wav.close()
        }

        val capped = !endedEarly && produced < targetFrames
        val error = when {
            capped -> "render exceeded ${maxWallMillis}ms wall cap"
            produced == 0L -> emulator.getLastError() ?: "no audio produced"
            else -> ""
        }
        return TrackMetrics(
            trackKey = track.trackKey,
            game = track.game,
            title = track.title,
            extension = track.extension,
            sampleRate = sampleRate,
            frames = produced,
            rmsDbfs = stats.rmsDbfs(),
            peakDbfs = stats.peakDbfs(),
            lufs = loudness.integratedLoudness(),
            pcmHash = stats.pcmHash(),
            error = error,
            wav = wavFile.name,
        )
    }

    private fun failure(track: SelectedTrack, wav: String, message: String) = TrackMetrics(
        trackKey = track.trackKey, game = track.game, title = track.title,
        extension = track.extension, sampleRate = 0, frames = 0L,
        rmsDbfs = Double.NEGATIVE_INFINITY, peakDbfs = Double.NEGATIVE_INFINITY, lufs = Double.NaN,
        pcmHash = 0L, error = message, wav = wav,
    )
}
