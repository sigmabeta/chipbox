@file:Suppress("FunctionName", "VariableNaming")

package net.sigmabeta.chipbox.js.speaker

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.Promise
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import net.sigmabeta.chipbox.player.buffer.AudioBuffer
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.common.DefaultResamplerFactory
import net.sigmabeta.chipbox.player.common.Resampler
import net.sigmabeta.chipbox.player.common.ResamplerQuality
import net.sigmabeta.chipbox.player.speaker.BaseSpeaker
import net.sigmabeta.chipbox.settings.ResamplerMode
import net.sigmabeta.sage.logging.Hatchet
import org.khronos.webgl.Float32Array
import org.w3c.dom.MessagePort
import org.w3c.dom.MessageEvent

/**
 * [BaseSpeaker] backed by Web Audio. Resamples each [AudioBuffer]'s S16 PCM to the AudioContext's
 * rate with the shared multiplatform [Resampler] (per the user's [ResamplerMode]), converts to
 * Float32, and posts it to `chipbox-audio-worklet.js` — now a plain FIFO that plays frames at the
 * context rate. AudioContext is created lazily on first audio — browsers require a user gesture.
 *
 * [ResamplerMode.OS] has no separate browser resampler for a worklet stream, so on web it maps to
 * the linear kernel (matching the worklet's previous built-in resampling); LINEAR/CUBIC select the
 * matching kernel. When the source rate already equals the context rate the resampler is bypassed.
 *
 * The worklet posts `{type: "consumed", frames}` back after each FIFO head shift (in output frames);
 * [awaitSinkCapacity] uses those to throttle the consume loop to actual playback rate, so
 * `SpeakerEvent.TrackChange` (and now-playing metadata) stays aligned with audible output.
 */
class WebAudioSpeaker(
    bufferManager: ConsumerBufferManager,
    hatchet: Hatchet,
    resamplerModes: Flow<ResamplerMode>,
) : BaseSpeaker(bufferManager, hatchet, Dispatchers.Default) {

    private val initScope = CoroutineScope(Dispatchers.Default)
    private var audioContext: AudioContext? = null
    private var workletNode: AudioWorkletNode? = null
    private var workletPort: MessagePort? = null

    // Latest selected mode (off the settings flow) and the mode the current resampler was built for;
    // JS is single-threaded so a plain var is safe. A change rebuilds the resampler on the next buffer.
    private var requestedMode: ResamplerMode = ResamplerMode.DEFAULT
    private var activeMode: ResamplerMode? = null
    private var currentInputRate: Int = 0
    private var resampler: Resampler? = null
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

    init {
        initScope.launch { resamplerModes.collect { requestedMode = it } }
    }

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

        val mode = requestedMode
        if (audio.sampleRate != currentInputRate || mode != activeMode) {
            currentInputRate = audio.sampleRate
            activeMode = mode
            resampler = buildResampler(mode, audio.sampleRate, outputRate)
        }

        // Produce interleaved S16 at the context rate (resampled, or the input as-is when bypassed),
        // then convert to Float32 and post. The worklet just plays it.
        val converter = resampler
        val outputData: ShortArray
        val outputFrames: Int
        if (converter == null) {
            outputData = audio.data
            outputFrames = audio.data.size / SHORTS_PER_FRAME
        } else {
            val inputFrames = audio.data.size / SHORTS_PER_FRAME
            val neededShorts = converter.maxOutputFrames(inputFrames) * SHORTS_PER_FRAME
            if (resampleBuffer.size < neededShorts) resampleBuffer = ShortArray(neededShorts)
            outputFrames = converter.process(audio.data, inputFrames, resampleBuffer)
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

    /** The resampler for [mode], or null to bypass when the rates already match. OS has no separate
     *  browser resampler for a worklet stream, so it uses the linear kernel here. */
    private fun buildResampler(mode: ResamplerMode, inputRate: Int, outputRate: Int): Resampler? {
        val quality = when (mode) {
            ResamplerMode.OS, ResamplerMode.LINEAR -> ResamplerQuality.LINEAR
            ResamplerMode.CUBIC -> ResamplerQuality.CUBIC
        }
        val factory = DefaultResamplerFactory(quality)
        return if (factory.needed(inputRate, outputRate)) factory.create(inputRate, outputRate) else null
    }

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
        currentInputRate = 0
        activeMode = null
        resampler = null
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
