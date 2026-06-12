@file:Suppress("FunctionName", "VariableNaming")

package net.sigmabeta.chipbox.js.speaker

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.Promise
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import net.sigmabeta.chipbox.player.buffer.AudioBuffer
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.resampler.Resampler
import net.sigmabeta.chipbox.player.resampler.ResamplerDebugInfo
import net.sigmabeta.chipbox.player.speaker.BaseSpeaker
import net.sigmabeta.sage.logging.Hatchet
import org.khronos.webgl.Float32Array
import org.w3c.dom.MessagePort
import org.w3c.dom.MessageEvent

/**
 * [BaseSpeaker] backed by Web Audio. Resamples each [AudioBuffer]'s S16 PCM to the AudioContext's
 * rate with the injected single-technique [resampler], converts to Float32, and posts it to
 * `chipbox-audio-worklet.js` — now a plain FIFO that plays frames at the context rate. AudioContext
 * is created lazily on first audio — browsers require a user gesture.
 *
 * The [resampler] is resolved from the user's saved setting when the graph builds this (changing it
 * takes effect on the next launch — no live switching). A worklet stream must always reach the
 * context rate, so OS mode maps to the linear kernel in DI rather than bypassing; only a source rate
 * already equal to the context rate skips resampling.
 *
 * The worklet posts `{type: "consumed", frames}` back after each FIFO head shift (in output frames);
 * [awaitSinkCapacity] uses those to throttle the consume loop to actual playback rate, so
 * `SpeakerEvent.TrackChange` (and now-playing metadata) stays aligned with audible output.
 */
class WebAudioSpeaker(
    bufferManager: ConsumerBufferManager,
    hatchet: Hatchet,
    private val resampler: Resampler?,
) : BaseSpeaker(bufferManager, hatchet, Dispatchers.Default) {

    private val initScope = CoroutineScope(Dispatchers.Default)
    private var audioContext: AudioContext? = null
    private var workletNode: AudioWorkletNode? = null
    private var workletPort: MessagePort? = null

    // Native rate of the audio currently flowing; a change is a discontinuity that resets the
    // resampler. JS is single-threaded so a plain var is safe.
    private var currentInputRate: Int = 0
    private var resampleBuffer: ShortArray = ShortArray(0)

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

    // Backpressure accounting in OUTPUT (context-rate) frames — both reset on flush.
    // `postedOutputFrames` is the cumulative total handed to the worklet since the last flush;
    // `consumedOutputFrames` is what the worklet has reported as fully output. The delta is the
    // queue depth `awaitSinkCapacity` keeps below [MAX_QUEUE_DEPTH_FRAMES].
    private var postedOutputFrames: Long = 0L
    private val consumedOutputFrames = MutableStateFlow(0L)

    private var initStarted = false

    override fun onAudioReceived(audio: AudioBuffer) {
        ensureAudioInitialized()
        val port = workletPort ?: return
        val ctx = audioContext ?: return
        val outputRate = ctx.sampleRate.toInt()

        val trackChanged = referenceTrackId != null && referenceTrackId != audio.trackId
        if (needsReferenceUpdate || trackChanged) {
            referenceContextTime = ctx.currentTime
            referenceTrackFrame = audio.frameIndex
            referenceSampleRate = audio.sampleRate
            needsReferenceUpdate = false
        }
        referenceTrackId = audio.trackId

        if (audio.sampleRate != currentInputRate) {
            currentInputRate = audio.sampleRate
            // A new input rate is a discontinuity; the resampler also self-resets, but clear it
            // here so the first buffer at the new rate never carries phase from the previous one.
            resampler?.reset()
            updateResamplerDebug(
                ResamplerDebugInfo(
                    mode = resampler?.let { it::class.simpleName } ?: "OS",
                    active = isResampling(outputRate),
                    inputRateHz = audio.sampleRate,
                    outputRateHz = outputRate,
                )
            )
        }

        // Produce interleaved S16 at the context rate (resampled, or the input as-is when bypassed),
        // then convert to Float32 and post. The worklet just plays it.
        val converter = resampler
        val outputData: ShortArray
        val outputFrames: Int
        if (converter == null || !isResampling(outputRate)) {
            outputData = audio.data
            outputFrames = audio.data.size / SHORTS_PER_FRAME
        } else {
            val inputFrames = audio.data.size / SHORTS_PER_FRAME
            val neededShorts = converter.maxOutputFrames(inputFrames, currentInputRate, outputRate) * SHORTS_PER_FRAME
            if (resampleBuffer.size < neededShorts) resampleBuffer = ShortArray(neededShorts)
            outputFrames = converter.process(audio.data, inputFrames, currentInputRate, outputRate, resampleBuffer)
            outputData = resampleBuffer
        }

        val sampleCount = outputFrames * SHORTS_PER_FRAME
        val floats = Float32Array(sampleCount)
        for (i in 0 until sampleCount) {
            floats.asDynamic()[i] = outputData[i].toFloat() / Short.MAX_VALUE.toFloat()
        }
        val message = js("{}")
        message.type = "buffer"
        message.samples = floats
        port.postMessage(message)
        postedOutputFrames += outputFrames.toLong()
    }

    /** True when the injected kernel actually converts — a kernel is set and the source rate differs
     *  from the context [outputRate]. */
    private fun isResampling(outputRate: Int): Boolean = resampler != null && currentInputRate != outputRate

    override suspend fun awaitSinkCapacity() {
        // Hold the consume loop until the worklet has caught up — keeps the next iteration's
        // `emitTrackChangeIfNeeded` aligned with the audio the user actually hears.
        consumedOutputFrames.first { consumed -> postedOutputFrames - consumed <= MAX_QUEUE_DEPTH_FRAMES }
    }

    override fun flushSink() {
        needsReferenceUpdate = true
        referenceTrackId = null
        postedOutputFrames = 0L
        consumedOutputFrames.value = 0L
        resampler?.reset()
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
        postedOutputFrames = 0L
        consumedOutputFrames.value = 0L
        // Reset the rate gate so the next play re-resets the (injected, fixed) resampler.
        currentInputRate = 0
        updateResamplerDebug(null)
        // AudioContext stays alive — closing it would re-trigger the user-gesture gate, so the
        // next track wouldn't play without another click.
    }

    override fun release() {
        super.release()
        initScope.cancel()
    }

    // BaseSpeaker.currentPositionMs is final and returns the value last sampled here on the consume
    // coroutine; we derive it from AudioContext.currentTime (which advances at realtime and freezes
    // under suspend()), so position survives pause/seek without per-buffer tracking.
    override fun readSinkPositionMs(): Long {
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
                    consumedOutputFrames.value = frames
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
        const val SHORTS_PER_FRAME = 2

        // Target queue depth in OUTPUT (context-rate) frames — ~1 second at 48 kHz. Large enough
        // that the worklet has audio to play through significant main-thread stalls (e.g. Coil image
        // decode while scrolling a grid), small enough that the visual track-change lag stays
        // imperceptible (the consume loop's `awaitSinkCapacity` keeps `emitTrackChangeIfNeeded`
        // within this window of actual audio output).
        const val MAX_QUEUE_DEPTH_FRAMES = 48_000L
    }
}

// Web Audio API — minimum surface. Kotlin/JS stdlib's bindings don't cover audioWorklet/suspend.

private external class AudioContext {
    val destination: AudioDestinationNode
    val audioWorklet: AudioWorklet
    val state: String
    val currentTime: Double
    val sampleRate: Double
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
