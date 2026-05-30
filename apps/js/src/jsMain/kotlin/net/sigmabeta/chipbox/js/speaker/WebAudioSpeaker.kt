@file:Suppress("FunctionName", "VariableNaming")

package net.sigmabeta.chipbox.js.speaker

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.Promise
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import net.sigmabeta.chipbox.player.buffer.AudioBuffer
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.speaker.BaseSpeaker
import net.sigmabeta.sage.logging.Hatchet
import org.khronos.webgl.Float32Array
import org.w3c.dom.MessagePort
import org.w3c.dom.MessageEvent

/**
 * [BaseSpeaker] backed by Web Audio. Converts each [AudioBuffer]'s S16 PCM to Float32 and
 * posts it to `chipbox-audio-worklet.js`, which resamples to the AudioContext's native rate.
 * AudioContext is created lazily on first audio — browsers require a user gesture.
 *
 * The worklet posts a `{type: "consumed", frames}` message back after each FIFO head shift;
 * [awaitSinkCapacity] uses those messages to throttle the consume loop to actual playback
 * rate. Without that throttle, postMessage's non-blocking nature lets the consume loop drain
 * BufferManager many seconds ahead of the worklet's actual output, which makes
 * `SpeakerEvent.TrackChange` (and therefore now-playing metadata) fire well before the audio
 * for the new track is audible.
 */
class WebAudioSpeaker(
    bufferManager: ConsumerBufferManager,
    hatchet: Hatchet,
) : BaseSpeaker(bufferManager, hatchet, Dispatchers.Default) {

    private val initScope = CoroutineScope(Dispatchers.Default)
    private var audioContext: AudioContext? = null
    private var workletNode: AudioWorkletNode? = null
    private var workletPort: MessagePort? = null

    // Set once on the first post-flush buffer (and again whenever the trackId in the buffer
    // stream changes — natural end → next track skips the flushSink path); AudioContext.currentTime
    // then drives the position at realtime. Updating per-buffer would race ahead of playback
    // because the consume loop drains BufferManager faster than realtime when the cache is far
    // render-ahead.
    private var referenceContextTime: Double = 0.0
    private var referenceTrackFrame: Long = 0L
    private var referenceSampleRate: Int = 0
    private var needsReferenceUpdate: Boolean = true
    private var referenceTrackId: Long? = null

    // Backpressure accounting — both counts reset on flush. `postedSourceFrames` is the
    // cumulative source-frame total handed to the worklet since the last flush;
    // `consumedSourceFrames` is what the worklet has reported as fully output. The delta is
    // the queue depth in source-frames that `awaitSinkCapacity` keeps below [MAX_QUEUE_DEPTH_FRAMES].
    private var postedSourceFrames: Long = 0L
    private val consumedSourceFrames = MutableStateFlow(0L)

    private var initStarted = false

    override fun onAudioReceived(audio: AudioBuffer) {
        ensureAudioInitialized()
        val port = workletPort ?: return
        val ctx = audioContext

        val trackChanged = referenceTrackId != null && referenceTrackId != audio.trackId
        if (ctx != null && (needsReferenceUpdate || trackChanged)) {
            referenceContextTime = ctx.currentTime
            referenceTrackFrame = audio.frameIndex
            referenceSampleRate = audio.sampleRate
            needsReferenceUpdate = false
        }
        referenceTrackId = audio.trackId

        val pcm = audio.data
        val floats = Float32Array(pcm.size)
        for (i in pcm.indices) {
            floats.asDynamic()[i] = pcm[i].toFloat() / Short.MAX_VALUE.toFloat()
        }
        val message = js("{}")
        message.type = "buffer"
        message.samples = floats
        message.srcRate = audio.sampleRate
        port.postMessage(message)
        // `pcm.size` is stereo-interleaved samples; divide by 2 to get frames.
        postedSourceFrames += (pcm.size / 2).toLong()
    }

    override suspend fun awaitSinkCapacity() {
        // Hold the consume loop until the worklet has caught up — keeps the next iteration's
        // `emitTrackChangeIfNeeded` aligned with the audio the user actually hears.
        consumedSourceFrames.first { posted -> postedSourceFrames - posted <= MAX_QUEUE_DEPTH_FRAMES }
    }

    override fun flushSink() {
        needsReferenceUpdate = true
        referenceTrackId = null
        postedSourceFrames = 0L
        consumedSourceFrames.value = 0L
        val port = workletPort ?: return
        val flush = js("{}")
        flush.type = "flush"
        port.postMessage(flush)
    }

    override fun onPaused() {
        // AudioContext.currentTime freezes under suspend(), so the position reference stays
        // valid across pause without any tracking on our end.
        audioContext?.suspend()
    }

    override fun onResumed() {
        audioContext?.resume()
    }

    override fun teardown() {
        workletNode?.disconnect()
        workletNode = null
        workletPort?.close()
        workletPort = null
        referenceSampleRate = 0
        referenceTrackId = null
        needsReferenceUpdate = true
        postedSourceFrames = 0L
        consumedSourceFrames.value = 0L
        // AudioContext stays alive — closing it would re-trigger the user-gesture gate, so the
        // next track wouldn't play without another click.
    }

    override fun currentPositionMs(): Long {
        val ctx = audioContext ?: return 0L
        val rate = referenceSampleRate
        if (rate <= 0) return 0L
        val elapsedSeconds = (ctx.currentTime - referenceContextTime).coerceAtLeast(0.0)
        val frame = referenceTrackFrame + (elapsedSeconds * rate).toLong()
        return frame * MILLIS_PER_SECOND / rate
    }

    private fun ensureAudioInitialized() {
        // BaseSpeaker.play() is synchronous, but `audioWorklet.addModule` returns a Promise —
        // launch the init on the side and accept that the first few buffers arrive before the
        // worklet is ready and get dropped. They're silent (leading-silence trim) anyway.
        if (initStarted) return
        initStarted = true
        initScope.launch {
            val ctx = AudioContext()
            ctx.audioWorklet.addModule(WORKLET_URL).await()
            val node = AudioWorkletNode(ctx, "chipbox-audio")
            node.connect(ctx.destination)
            // Modern browsers start the context suspended until a user gesture has run.
            if (ctx.state == "suspended") ctx.resume().await()
            node.port.onmessage = { event: MessageEvent ->
                val data = event.data
                if (data != null && data.asDynamic().type == "consumed") {
                    val frames = (data.asDynamic().frames as Number).toLong()
                    consumedSourceFrames.value = frames
                }
            }
            audioContext = ctx
            workletNode = node
            workletPort = node.port
            // Release the AudioContext + worklet when the page goes away. `pagehide` fires
            // reliably on tab close, navigation, and the bf-cache transition; `beforeunload`
            // would block on user prompts in some configurations. Without this, the
            // AudioContext + worklet thread + MediaStreamAudioDestinationNode leak until
            // garbage collection picks them up (often never, for a closing tab).
            window.addEventListener("pagehide", { _ -> ctx.close() })
        }
    }

    private companion object {
        const val WORKLET_URL = "/wasm/chipbox-audio-worklet.js"
        const val MILLIS_PER_SECOND = 1_000L

        // Target queue depth in source-frames. ~1 second at 44.1 kHz — large enough that the
        // worklet has audio to play through significant main-thread stalls (e.g. Coil image
        // decode while scrolling a grid), small enough that the visual track-change lag stays
        // imperceptible (the consume loop's `awaitSinkCapacity` keeps `emitTrackChangeIfNeeded`
        // within this window of actual audio output).
        //
        // 200 ms was the original tuning but starved the worklet during scroll-heavy use.
        const val MAX_QUEUE_DEPTH_FRAMES = 44_100L
    }
}

// Web Audio API — minimum surface. Kotlin/JS stdlib's bindings don't cover audioWorklet/suspend.

private external class AudioContext {
    val destination: AudioDestinationNode
    val audioWorklet: AudioWorklet
    val state: String
    val currentTime: Double
    fun resume(): Promise<Unit>
    fun suspend(): Promise<Unit>
    fun close(): Promise<Unit>
}

private external class AudioDestinationNode

private external class AudioWorklet {
    fun addModule(moduleUrl: String): Promise<Unit>
}

private external class AudioWorkletNode(context: AudioContext, name: String) {
    val port: MessagePort
    fun connect(destination: AudioDestinationNode)
    fun disconnect()
}

private suspend fun <T> Promise<T>.await(): T = suspendCancellableCoroutine { cont ->
    then(
        { value ->
            cont.resume(value)
            undefined
        },
        { err ->
            cont.resumeWithException(RuntimeException("Promise rejected: $err"))
            undefined
        },
    )
}
