package net.sigmabeta.chipbox.player.speaker.real

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import java.util.concurrent.Executors
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.player.buffer.AudioBuffer
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.common.DefaultResamplerFactory
import net.sigmabeta.chipbox.player.common.Resampler
import net.sigmabeta.chipbox.player.common.ResamplerQuality
import net.sigmabeta.chipbox.player.speaker.BaseSpeaker
import net.sigmabeta.chipbox.settings.ResamplerMode
import net.sigmabeta.sage.logging.Hatchet

/**
 * Production [Speaker] that writes PCM to an Android [AudioTrack].
 *
 * Sample-rate handling follows the user's [ResamplerMode] ([resamplerModes]). For LINEAR/CUBIC the
 * AudioTrack is opened at the device's output rate ([outputSampleRateHz]) and each buffer is
 * resampled to it in-app — handing the platform mixer a non-standard rate (e.g. an N64 rip's 32006
 * Hz) makes it take an arbitrary-ratio resampler path that underruns a minimal buffer, so resampling
 * ourselves keeps AudioFlinger on a clean, standard rate. For [ResamplerMode.OS] the track is opened
 * at the native rate and the OS mixer resamples (the pre-resampler behaviour). The track + resampler
 * are rebuilt when the input rate or the selected mode changes.
 *
 * The consume loop runs on a single dedicated thread ([newSinkDispatcher]) held at
 * [Process.THREAD_PRIORITY_URGENT_AUDIO], not on `Dispatchers.Default`. A pooled-dispatcher
 * coroutine resumes on an arbitrary worker after each suspension point (an event emit, a buffer
 * await), so an `AudioTrack.write()` — and any thread-priority bump meant to protect it — would
 * otherwise land on a different thread each time. Pinning the loop guarantees every write runs on
 * the urgent-priority audio thread and serializes sink access without extra synchronisation.
 */
class RealSpeaker(
        bufferManager: ConsumerBufferManager,
        hatchet: Hatchet,
        resamplerModes: Flow<ResamplerMode>,
        private val outputSampleRateHz: Int,
        private val dispatcher: CoroutineDispatcher = newSinkDispatcher(),
) : BaseSpeaker(bufferManager, hatchet, dispatcher) {
    // @Volatile: written on the consume coroutine (initializeAudioTrack) and on the lifecycle
    // caller's thread (teardown, after the consume loop is cancel-joined). Keeps a stale non-null
    // reference from being observed across that handoff.
    @Volatile
    private var audioTrack: AudioTrack? = null

    // Latest selected mode (updated off the settings flow on configScope); read on the consume
    // thread. @Volatile for that cross-thread handoff. Defaults until the flow's first emission.
    @Volatile
    private var requestedMode: ResamplerMode = ResamplerMode.DEFAULT

    // The mode the current AudioTrack + resampler were built for; a change forces a rebuild.
    private var activeMode: ResamplerMode? = null

    // Collects the resampler-mode setting so a change applies on the next buffer.
    private val configScope = CoroutineScope(Dispatchers.Default)

    // Native rate of the audio currently flowing (the cache/emulator rate), distinct from the
    // AudioTrack's output rate. Drives when to rebuild the track + resampler.
    private var currentInputRate: Int = 0

    // Converts currentInputRate -> the track's output rate; null when bypassed (OS mode, or the
    // rates already match).
    private var resampler: Resampler? = null

    // Scratch for resampled output, grown on demand and reused across buffers.
    private var resampleBuffer: ShortArray = ShortArray(0)

    private var lastLoggedTrackId: Long? = null

    // Snapshot of "AudioTrack head when this buffer was queued" + "the track-frame that
    // buffer began at." currentPositionMs reads (head_now - referenceHeadFrames) and adds
    // referenceTrackFrame to convert the AudioTrack's since-creation counter into a position
    // within the song being played. Updated on every onAudioReceived so seeks and track
    // changes (where frameIndex jumps) are picked up automatically.
    private var referenceHeadFrames: Long = 0L
    private var referenceTrackFrame: Long = 0L

    init {
        configScope.launch {
            resamplerModes.collect { requestedMode = it }
        }
    }

    override fun onAudioReceived(audio: AudioBuffer) {
        // Rebuild when the input rate changes, the mode changed, or the track was torn down (null).
        // The track runs at the mode's output rate, so we can't key off audioTrack.sampleRate here.
        val mode = requestedMode
        if (audioTrack == null || audio.sampleRate != currentInputRate || mode != activeMode) {
            currentInputRate = audio.sampleRate
            activeMode = mode
            // OS mode keeps the native rate and lets AudioFlinger resample; otherwise open at the
            // device rate and resample in-app to it.
            val outputRate = if (mode == ResamplerMode.OS) audio.sampleRate else outputSampleRateHz
            audioTrack = initializeAudioTrack(outputRate)
            resampler = buildResampler(mode, audio.sampleRate, outputRate)
            hatchet.i(
                "Audiotrack setup complete: input ${audio.sampleRate} Hz -> output $outputRate Hz " +
                    "(mode=$mode, resampling=${resampler != null})."
            )
            audioTrack!!.play()
        }

        if (audio.trackId != lastLoggedTrackId) {
            hatchet.i(
                "onAudioReceived: first buffer for track ${audio.trackId} " +
                    "(prev=$lastLoggedTrackId, frameIndex=${audio.frameIndex}, " +
                    "rate=${audio.sampleRate})."
            )
            lastLoggedTrackId = audio.trackId
        }

        referenceHeadFrames = audioTrack!!.playbackHeadPosition.toLong()
        referenceTrackFrame = audio.frameIndex

        logProblems(writeToTrack(audio))
    }

    /** Write [audio] to the track, resampling to the output rate first when [resampler] is set.
     *  Returns AudioTrack.write's result (sample count or an error code). */
    private fun writeToTrack(audio: AudioBuffer): Int {
        val track = audioTrack!!
        val converter = resampler
            ?: return track.write(audio.data, 0, audio.data.size) // already at the device rate

        val inputFrames = audio.data.size / SHORTS_PER_FRAME
        val neededShorts = converter.maxOutputFrames(inputFrames) * SHORTS_PER_FRAME
        if (resampleBuffer.size < neededShorts) resampleBuffer = ShortArray(neededShorts)
        val outputFrames = converter.process(audio.data, inputFrames, resampleBuffer)
        return track.write(resampleBuffer, 0, outputFrames * SHORTS_PER_FRAME)
    }

    /** The resampler for [mode], or null to bypass (OS mode, or the rates already match). */
    private fun buildResampler(mode: ResamplerMode, inputRate: Int, outputRate: Int): Resampler? {
        val quality = when (mode) {
            ResamplerMode.OS -> return null
            ResamplerMode.LINEAR -> ResamplerQuality.LINEAR
            ResamplerMode.CUBIC -> ResamplerQuality.CUBIC
        }
        val factory = DefaultResamplerFactory(quality)
        return if (factory.needed(inputRate, outputRate)) factory.create(inputRate, outputRate) else null
    }

    override fun readSinkPositionMs(): Long {
        val track = audioTrack ?: return 0L
        val outRate = track.sampleRate
        val inRate = currentInputRate
        if (outRate <= 0 || inRate <= 0) return 0L
        // Called only on the consume coroutine, which also owns teardown — so the track can't be
        // released mid-call here. The try/catch stays as belt-and-suspenders: playbackHeadPosition
        // is a JNI call that throws IllegalStateException if the native AudioTrack was freed.
        val headFrames = try {
            track.playbackHeadPosition.toLong()
        } catch (_: IllegalStateException) {
            return 0L
        }
        // referenceTrackFrame is in input (track-native) frames; the AudioTrack head advances in
        // output (device) frames. Convert both to time so the position is right whether or not we're
        // resampling (when inRate == outRate this reduces to the old single-rate form).
        val elapsedOutFrames = (headFrames - referenceHeadFrames).coerceAtLeast(0L)
        val referenceMs = referenceTrackFrame * MILLIS_PER_SECOND / inRate
        val elapsedMs = elapsedOutFrames * MILLIS_PER_SECOND / outRate
        return referenceMs + elapsedMs
    }

    private fun initializeAudioTrack(
        outputRate: Int,
    ): AudioTrack {
        teardown()

        val bufferSizeBytes = AudioTrack.getMinBufferSize(
                outputRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
        )

        hatchet.v("Initializing audio track. Output rate: $outputRate Hz, Buffer size: $bufferSizeBytes bytes")

        val audioAttributes = AudioAttributes.Builder().apply {
            setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            setUsage(AudioAttributes.USAGE_MEDIA)
        }.build()

        val audioFormat = AudioFormat.Builder().apply {
            setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            setSampleRate(outputRate)
            setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
        }.build()

        return AudioTrack.Builder().apply {
            setAudioAttributes(audioAttributes)
            setAudioFormat(audioFormat)
            setBufferSizeInBytes(bufferSizeBytes)
            setTransferMode(AudioTrack.MODE_STREAM)
        }.build()
    }

    override fun onPaused() {
        audioTrack?.pause()
    }

    override fun onResumed() {
        audioTrack?.play()
    }

    override fun teardown() {
        // Null the (@Volatile) field out before releasing so any later reader sees null and bails
        // at the null-check instead of holding a reference to a track whose native pointer is
        // about to be freed. Teardown runs after the consume loop is cancel-joined, so it can't
        // overlap readSinkPositionMs/onAudioReceived.
        val track = audioTrack ?: return
        audioTrack = null
        referenceHeadFrames = 0L
        referenceTrackFrame = 0L

        hatchet.i("Tearing down audiotrack.")
        track.pause()
        track.flush()
        track.release()
    }

    override fun release() {
        super.release()
        configScope.cancel()
        // Shut down the dedicated audio thread we created in the default constructor. A
        // caller-supplied dispatcher (e.g. a test's) isn't ours to close, so only an
        // ExecutorCoroutineDispatcher — what newSinkDispatcher() returns — is closed.
        (dispatcher as? ExecutorCoroutineDispatcher)?.close()
    }

    override fun flushSink() {
        val track = audioTrack ?: return
        hatchet.d("flushSink: audioTrack.pause()")
        track.pause()
        hatchet.d("flushSink: audioTrack.flush()")
        track.flush()
        // Drop the resampler's phase + history so post-seek frames don't interpolate across the gap.
        resampler?.reset()
        hatchet.d("flushSink: audioTrack.play()")
        track.play()
        hatchet.d("flushSink: complete.")
    }

    private fun logProblems(samplesWritten: Int) {
        val error = when (samplesWritten) {
            AudioTrack.ERROR_INVALID_OPERATION -> "Invalid AudioTrack operation."
            AudioTrack.ERROR_BAD_VALUE -> "Invalid AudioTrack value."
            AudioTrack.ERROR -> "Unknown AudioTrack error."
            else -> null
        }

        if (error != null) {
            hatchet.e(error)
            emitError(error)
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L

        /** Interleaved stereo 16-bit PCM: 2 shorts per frame. */
        const val SHORTS_PER_FRAME = 2

        private const val AUDIO_THREAD_NAME = "ChipboxAudioSink"

        /**
         * One dedicated daemon thread for the consume loop, pinned at
         * [Process.THREAD_PRIORITY_URGENT_AUDIO] from creation so every `AudioTrack.write()` runs at
         * audio priority (see the class doc). The priority is set inside the thread factory so a
         * replacement thread — if the worker ever dies — is pinned too; [release] shuts it down.
         */
        private fun newSinkDispatcher(): ExecutorCoroutineDispatcher =
            Executors.newSingleThreadExecutor { runnable ->
                Thread({
                    Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                    runnable.run()
                }, AUDIO_THREAD_NAME).apply { isDaemon = true }
            }.asCoroutineDispatcher()
    }
}
