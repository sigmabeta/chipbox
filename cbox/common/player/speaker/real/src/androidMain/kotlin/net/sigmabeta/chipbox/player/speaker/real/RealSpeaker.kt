package net.sigmabeta.chipbox.player.speaker.real

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import java.util.concurrent.Executors
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import net.sigmabeta.chipbox.player.buffer.AudioBuffer
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.speaker.BaseSpeaker
import net.sigmabeta.sage.logging.Hatchet

/**
 * Production [Speaker] that writes PCM to an Android [AudioTrack].
 *
 * The track is lazily (re)created whenever an incoming buffer's sample rate differs from the
 * current track's — emulators within a setlist may use different rates, so the speaker can't
 * commit to a single configuration up front.
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
        private val dispatcher: CoroutineDispatcher = newSinkDispatcher(),
) : BaseSpeaker(bufferManager, hatchet, dispatcher) {
    // @Volatile: written on the consume coroutine (initializeAudioTrack) and on the lifecycle
    // caller's thread (teardown, after the consume loop is cancel-joined). Keeps a stale non-null
    // reference from being observed across that handoff.
    @Volatile
    private var audioTrack: AudioTrack? = null

    private var lastLoggedTrackId: Long? = null

    // Snapshot of "AudioTrack head when this buffer was queued" + "the track-frame that
    // buffer began at." currentPositionMs reads (head_now - referenceHeadFrames) and adds
    // referenceTrackFrame to convert the AudioTrack's since-creation counter into a position
    // within the song being played. Updated on every onAudioReceived so seeks and track
    // changes (where frameIndex jumps) are picked up automatically.
    private var referenceHeadFrames: Long = 0L
    private var referenceTrackFrame: Long = 0L

    override fun onAudioReceived(audio: AudioBuffer) {
        if (audio.sampleRate != audioTrack?.sampleRate) {
            hatchet.d("New sample rate: ${audio.sampleRate}")
            audioTrack = initializeAudioTrack(audio.sampleRate)
            hatchet.d("Audiotrack setup complete!")

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

        // Samples, not Frames
        val samplesWritten = audioTrack!!.write(
                audio.data,
                0,
                audio.data.size
        )

        logProblems(samplesWritten)
    }

    override fun readSinkPositionMs(): Long {
        val track = audioTrack ?: return 0L
        val rate = track.sampleRate
        if (rate <= 0) return 0L
        // Called only on the consume coroutine, which also owns teardown — so the track can't be
        // released mid-call here. The try/catch stays as belt-and-suspenders: playbackHeadPosition
        // is a JNI call that throws IllegalStateException if the native AudioTrack was freed.
        val headFrames = try {
            track.playbackHeadPosition.toLong()
        } catch (_: IllegalStateException) {
            return 0L
        }
        val elapsedFrames = (headFrames - referenceHeadFrames).coerceAtLeast(0L)
        val frame = referenceTrackFrame + elapsedFrames
        return frame * MILLIS_PER_SECOND / rate
    }

    private fun initializeAudioTrack(
        sampleRate: Int,
    ): AudioTrack {
        teardown()

        val bufferSizeBytes = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
        )

        hatchet.v("Initializing audio track. Sample Rate: $sampleRate Hz, Buffer size: $bufferSizeBytes bytes")

        val audioAttributes = AudioAttributes.Builder().apply {
            setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            setUsage(AudioAttributes.USAGE_MEDIA)
        }.build()

        val audioFormat = AudioFormat.Builder().apply {
            setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            setSampleRate(sampleRate)
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
