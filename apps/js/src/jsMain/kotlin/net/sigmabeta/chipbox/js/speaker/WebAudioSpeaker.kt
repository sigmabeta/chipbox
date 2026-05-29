@file:Suppress("FunctionName", "VariableNaming")

package net.sigmabeta.chipbox.js.speaker

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.Promise
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import net.sigmabeta.chipbox.player.buffer.AudioBuffer
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.speaker.BaseSpeaker
import net.sigmabeta.sage.logging.Hatchet
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Float32Array
import org.khronos.webgl.set
import org.w3c.dom.MessagePort

/**
 * [BaseSpeaker] subclass that pumps PCM to the browser's Web Audio output via an
 * [AudioWorkletNode]. The worklet processor (`chipbox-audio-worklet.js`) runs on the dedicated
 * audio thread; this class converts each incoming [AudioBuffer]'s S16 PCM to Float32 and
 * `postMessage`s it to the worklet, which fills the output AudioBuffer from the queue.
 *
 * #### Sample rate
 * The AudioContext's native rate (hardware-dependent, usually 44100 or 48000) is what the
 * worklet must output. For v1 we don't resample — chiptune emulators output 44100 or 32000
 * (SPC). When the rates match the output is correct; when they don't, the track plays at the
 * wrong speed. Adding a simple linear or cubic resampler in [onAudioReceived] is a follow-up.
 *
 * #### User-gesture gating
 * Browsers refuse to start an AudioContext outside a user-initiated event. We construct the
 * context lazily on first [play] — by then a click in the UI has unlocked audio. If the context
 * starts in `suspended` state we call `resume()`.
 *
 * #### Lifecycle
 * `BaseSpeaker.release()` cancels the consume scope but doesn't close the AudioContext (the
 * page might still want audio later). [stop] closes the worklet's port; a full app teardown
 * would also `audioContext.close()`, but that's not currently triggered anywhere.
 */
class WebAudioSpeaker(
    bufferManager: ConsumerBufferManager,
    hatchet: Hatchet,
) : BaseSpeaker(bufferManager, hatchet, Dispatchers.Default) {

    private val initScope = CoroutineScope(Dispatchers.Default)
    private var audioContext: AudioContext? = null
    private var workletNode: AudioWorkletNode? = null
    private var workletPort: MessagePort? = null

    /**
     * Tracks "have we kicked off the async init yet?" — the actual context+worklet construction
     * is suspend (`audioWorklet.addModule` returns a Promise), but BaseSpeaker.play() is sync.
     * First call to onAudioReceived triggers the init coroutine if not already running; until
     * it completes, the first few buffers are dropped (acceptable — they're silent during
     * leading-silence trim anyway).
     */
    private var initStarted = false

    override fun onAudioReceived(audio: AudioBuffer) {
        ensureAudioInitialized()
        val port = workletPort ?: return

        // Convert S16 LE → F32 in [-1, 1]. Allocate a fresh Float32Array per buffer — postMessage
        // will structured-clone it (or we could transfer it; cloning is fine, GC handles cleanup).
        val pcm = audio.data
        val floats = Float32Array(pcm.size)
        for (i in pcm.indices) {
            floats.asDynamic()[i] = pcm[i].toFloat() / Short.MAX_VALUE.toFloat()
        }
        // Tag every buffer with the emulator's source rate; the worklet linear-interp resamples
        // to the AudioContext rate per output frame. Different emulators output different rates
        // (libgme: 44100 for most, 32000 for SPC); the rate can change between consecutive
        // buffers when the director switches tracks.
        val message = js("{}")
        message.type = "buffer"
        message.samples = floats
        message.srcRate = audio.sampleRate
        port.postMessage(message)
    }

    override fun flushSink() {
        val port = workletPort ?: return
        val flush = js("{}")
        flush.type = "flush"
        port.postMessage(flush)
    }

    override fun teardown() {
        workletNode?.disconnect()
        workletNode = null
        workletPort?.close()
        workletPort = null
        // Leave the AudioContext alive — next play() will reuse it. Closing+reopening the
        // context per session would re-trigger the user-gesture gate.
    }

    override fun currentPositionMs(): Long {
        // The worklet exposes its play head via postMessage in principle; for v1 we don't track
        // it. BaseSpeaker uses 0 by default — return the same to be explicit.
        return 0L
    }

    private fun ensureAudioInitialized() {
        if (initStarted) return
        initStarted = true
        initScope.launch {
            try {
                val ctx = AudioContext()
                ctx.audioWorklet.addModule(WORKLET_URL).await()
                val node = AudioWorkletNode(ctx, "chipbox-audio")
                node.connect(ctx.destination)
                if (ctx.state == "suspended") {
                    ctx.resume().await()
                }
                audioContext = ctx
                workletNode = node
                workletPort = node.port
            } catch (t: Throwable) {
                emitError("WebAudioSpeaker init failed: ${t.message}")
                initStarted = false  // allow retry on next play
            }
        }
    }

    private companion object {
        const val WORKLET_URL = "/wasm/chipbox-audio-worklet.js"
    }
}

// ---------------------------------------------------------------------------------------------
// Web Audio API — minimum-surface external declarations. Kotlin's web stdlib has these in
// org.w3c.dom but they're spread across packages we'd need to opt into individually.

private external class AudioContext {
    val destination: AudioDestinationNode
    val audioWorklet: AudioWorklet
    val state: String
    fun resume(): Promise<Unit>
}

private external class AudioDestinationNode

private external class AudioWorklet {
    fun addModule(moduleUrl: String): Promise<Unit>
}

private external class AudioWorkletNode(
    context: AudioContext,
    name: String,
) {
    val port: MessagePort
    fun connect(destination: AudioDestinationNode)
    fun disconnect()
}

// Helper to convert a JS Promise to a suspend call.
private suspend fun <T> Promise<T>.await(): T = suspendCancellableCoroutine { cont ->
    then(
        { value -> cont.resume(value); undefined },
        { err -> cont.resumeWithException(RuntimeException("Promise rejected: $err")); undefined },
    )
}
