@file:Suppress("FunctionName", "VariableNaming")

package net.sigmabeta.chipbox.js.speaker

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.Promise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import net.sigmabeta.chipbox.player.buffer.AudioBuffer
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.speaker.BaseSpeaker
import net.sigmabeta.sage.logging.Hatchet
import org.khronos.webgl.Float32Array
import org.w3c.dom.MessagePort

/**
 * [BaseSpeaker] backed by Web Audio. Converts each [AudioBuffer]'s S16 PCM to Float32 and
 * posts it to `chipbox-audio-worklet.js`, which resamples to the AudioContext's native rate.
 * AudioContext is created lazily on first audio — browsers require a user gesture.
 */
class WebAudioSpeaker(
    bufferManager: ConsumerBufferManager,
    hatchet: Hatchet,
) : BaseSpeaker(bufferManager, hatchet, Dispatchers.Default) {

    private val initScope = CoroutineScope(Dispatchers.Default)
    private var audioContext: AudioContext? = null
    private var workletNode: AudioWorkletNode? = null
    private var workletPort: MessagePort? = null

    // Set once on the first post-flush buffer; AudioContext.currentTime then drives the position
    // at realtime. Re-armed by flushSink (seek + track change). Updating per-buffer would race
    // ahead of playback because the consume loop drains BufferManager faster than realtime when
    // the cache is far render-ahead.
    private var referenceContextTime: Double = 0.0
    private var referenceTrackFrame: Long = 0L
    private var referenceSampleRate: Int = 0
    private var needsReferenceUpdate: Boolean = true

    private var initStarted = false

    override fun onAudioReceived(audio: AudioBuffer) {
        ensureAudioInitialized()
        val port = workletPort ?: return
        val ctx = audioContext

        if (ctx != null && needsReferenceUpdate) {
            referenceContextTime = ctx.currentTime
            referenceTrackFrame = audio.frameIndex
            referenceSampleRate = audio.sampleRate
            needsReferenceUpdate = false
        }

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
    }

    override fun flushSink() {
        needsReferenceUpdate = true
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
        needsReferenceUpdate = true
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
            audioContext = ctx
            workletNode = node
            workletPort = node.port
        }
    }

    private companion object {
        const val WORKLET_URL = "/wasm/chipbox-audio-worklet.js"
        const val MILLIS_PER_SECOND = 1_000L
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
        { value -> cont.resume(value); undefined },
        { err -> cont.resumeWithException(RuntimeException("Promise rejected: $err")); undefined },
    )
}
