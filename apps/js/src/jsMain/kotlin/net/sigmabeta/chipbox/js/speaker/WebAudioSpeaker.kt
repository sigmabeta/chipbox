@file:Suppress("FunctionName", "VariableNaming")

package net.sigmabeta.chipbox.js.speaker

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.Promise
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import net.sigmabeta.chipbox.player.buffer.AudioBuffer
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.resampler.Resampler
import net.sigmabeta.chipbox.player.resampler.ResamplerDebugInfo
import net.sigmabeta.chipbox.player.speaker.AudioInitEvent
import net.sigmabeta.chipbox.player.speaker.AudioInitPhase
import net.sigmabeta.chipbox.player.speaker.AudioInitState
import net.sigmabeta.chipbox.player.speaker.BaseSpeaker
import net.sigmabeta.chipbox.player.speaker.reduce
import net.sigmabeta.sage.logging.Hatchet
import org.khronos.webgl.Float32Array
import org.w3c.dom.MessagePort
import org.w3c.dom.MessageEvent

/**
 * [BaseSpeaker] backed by Web Audio. Resamples each [AudioBuffer]'s S16 PCM to the AudioContext's
 * rate with the injected single-technique [resampler], converts to Float32, and posts it to
 * `chipbox-audio-worklet.js` — now a plain FIFO that plays frames at the context rate.
 *
 * ### Gesture unlock
 * Browsers only let an `AudioContext` start from a user gesture; Safari is strict about the
 * `resume()` call being inside the click handler. [prepareForPlayback] creates the context and
 * resumes it synchronously from [net.sigmabeta.chipbox.player.director.Director.request], so the
 * first play is audible without a second click.
 *
 * ### Worklet lifecycle
 * The node/context survive [teardown] (a stop only flushes the FIFO) — rebuilding them on every
 * play would re-trigger the autoplay gate and, historically, leave the sink permanently mute after
 * an end-of-setlist. Initialization is modelled by [AudioInitState] (`Idle → Preparing → Ready |
 * Failed`) with a bounded timeout and one retry; a terminal failure emits a visible
 * [net.sigmabeta.chipbox.player.speaker.SpeakerEvent.Error]. The consume loop waits for
 * [awaitSinkReady] before dequeuing a buffer, so frames that arrive while the worklet loads are
 * held by producer backpressure instead of being dropped, and a failed/suspended sink can neither
 * drain a track at CPU speed nor block the loop forever.
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

    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var initJob: Job? = null
    private val initState = MutableStateFlow(AudioInitState())

    private var audioContext: AudioContext? = null
    private var workletNode: AudioWorkletNode? = null
    private var workletPort: MessagePort? = null
    private var pageHideInstalled = false

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

    override fun prepareForPlayback() {
        val ctx = ensureContext() ?: return
        if (initState.value.isReady) {
            // Already unlocked; a pause (or an OS policy) may have suspended the context.
            resumeContext(ctx)
            return
        }
        beginInit(ctx)
    }

    override suspend fun awaitSinkReady(): Boolean {
        val current = initState.value
        if (current.isReady) return true

        // Defensive: if playback was started through a path that did not go through the gesture
        // seam, start initialization here rather than waiting for an init that was never begun.
        if (current.phase == AudioInitPhase.IDLE || current.mayRetry) {
            val ctx = ensureContext()
            if (ctx != null) beginInit(ctx)
        }

        val settled = when {
            initState.value.isReady -> initState.value
            initState.value.isTerminalFailure -> initState.value
            else -> withTimeoutOrNull(READY_WAIT_TIMEOUT_MS) {
                initState.first { it.isReady || it.isTerminalFailure }
            }
        }

        if (settled == null) {
            failTerminal("the AudioWorklet didn't become ready in time")
            return false
        }
        return settled.isReady
    }

    override fun onAudioReceived(audio: AudioBuffer) {
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
        val ctx = audioContext
        // A paused context is expected to make no progress; the next gesture resumes it. Outside
        // pause, a suspended context means the browser blocked audio — don't wait forever for a
        // `consumed` message that will never come.
        if (ctx != null && ctx.state == "suspended") resumeContext(ctx)

        val caughtUp = withTimeoutOrNull(CAPACITY_WAIT_TIMEOUT_MS) {
            consumedOutputFrames.first { consumed -> postedOutputFrames - consumed <= MAX_QUEUE_DEPTH_FRAMES }
        }
        if (caughtUp == null) {
            val reason = when (audioContext?.state) {
                "suspended" -> "the AudioContext is suspended (the browser blocked playback; press play again)"
                "closed" -> "the AudioContext was closed"
                else -> "the audio worklet stopped reporting progress"
            }
            emitError("Audio output stalled: $reason.")
            // The director responds to SpeakerEvent.Error with StopSpeaker, which cancels this
            // loop; waiting for that cancellation cannot spin or drain the buffer manager.
            awaitCancellation()
        }
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
        val ctx = audioContext ?: return
        if (ctx.state != "suspended") {
            ctx.suspend().ignoreFailure("suspend", hatchet)
        }
    }

    override fun onResumed() {
        audioContext?.let(::resumeContext)
    }

    override fun teardown() {
        // Stop only discards queued audio. The node + context intentionally survive: closing and
        // rebuilding them would re-trigger the user-gesture gate on the next play (and the old
        // `initStarted = true` latch left every play after an end-of-setlist silent).
        flushSink()
        referenceSampleRate = 0
        referenceTrackId = null
        needsReferenceUpdate = true
        // Reset the rate gate so the next play re-resets the (injected, fixed) resampler.
        currentInputRate = 0
        updateResamplerDebug(null)

        // A stop while init was in flight or failed abandons that attempt; the next play gets a
        // fresh two-attempt budget. A ready sink keeps its state so playback is instantly usable.
        val current = initState.value
        if (current.phase == AudioInitPhase.PREPARING || current.phase == AudioInitPhase.FAILED) {
            initJob?.cancel()
            initJob = null
            initState.value = current.reduce(AudioInitEvent.Reset)
        }
    }

    override fun release() {
        super.release()
        initJob?.cancel()
        initJob = null
        initScope.cancel()
        audioContext?.let { closeContext(it) }
        audioContext = null
        workletNode = null
        workletPort = null
        initState.value = AudioInitState()
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

    // ---- initialization ----

    private fun beginInit(ctx: AudioContext) {
        val current = initState.value
        val next = current.reduce(AudioInitEvent.Prepare)
        if (next.phase != AudioInitPhase.PREPARING || next == current) return
        initState.value = next
        // Resume inside this synchronous call (the user gesture) before the async module load.
        resumeContext(ctx)
        launchInitAttempt(ctx)
    }

    private fun launchInitAttempt(ctx: AudioContext) {
        initJob = initScope.launch {
            val completed = withTimeoutOrNull(INIT_TIMEOUT_MS) {
                if (workletPort == null) {
                    ctx.audioWorklet.addModule(WORKLET_URL).await("AudioWorklet.addModule")
                    val node = AudioWorkletNode(ctx, "chipbox-audio")
                    node.connect(ctx.destination)
                    node.port.onmessage = ::onWorkletMessage
                    workletNode = node
                    workletPort = node.port
                }
                // Modern browsers start the context suspended until a user gesture has run.
                if (ctx.state == "suspended") ctx.resume().await("AudioContext.resume")
                Unit
            }
            if (completed == null) {
                onInitAttemptFailed(
                    ctx,
                    "timed out after ${INIT_TIMEOUT_MS / MILLIS_PER_SECOND} seconds",
                )
            } else {
                initState.value = initState.value.reduce(AudioInitEvent.Succeeded)
                hatchet.i("WebAudioSpeaker: worklet ready (rate=${ctx.sampleRate}).")
            }
        }
    }

    private fun onInitAttemptFailed(ctx: AudioContext, message: String) {
        var next = initState.value.reduce(AudioInitEvent.Failed(message))
        initState.value = next
        if (next.mayRetry) {
            hatchet.w("WebAudioSpeaker: init failed ($message); retrying once.")
            next = next.reduce(AudioInitEvent.Prepare)
            initState.value = next
            if (next.phase == AudioInitPhase.PREPARING) launchInitAttempt(ctx)
        } else if (next.isTerminalFailure) {
            hatchet.e("WebAudioSpeaker: init failed permanently: $message")
            emitError("Audio output couldn't start: $message.")
        }
    }

    /**
     * Backstop for a preparation that never settles (e.g. the init coroutine was starved). Mark the
     * state terminal so [awaitSinkReady] returns and the consume loop exits; teardown resets it so
     * the next play can try again.
     */
    private fun failTerminal(message: String) {
        if (initState.value.isTerminalFailure) return
        initState.value = initState.value.copy(
            phase = AudioInitPhase.FAILED,
            attempts = maxOf(initState.value.attempts, AudioInitState.MAX_ATTEMPTS),
            message = message,
        )
        hatchet.e("WebAudioSpeaker: $message")
        emitError("Audio output couldn't start: $message.")
    }

    private fun onWorkletMessage(event: MessageEvent) {
        val data = event.data
        if (data != null && data.asDynamic().type == "consumed") {
            val frames = (data.asDynamic().frames as Number).toLong()
            consumedOutputFrames.value = frames
        }
    }

    private fun ensureContext(): AudioContext? {
        val existing = audioContext
        if (existing != null && existing.state != "closed") return existing
        if (existing != null) {
            // The page was in the back/forward cache and the context was closed by `pagehide`.
            audioContext = null
            workletNode = null
            workletPort = null
            pageHideInstalled = false
            initState.value = initState.value.reduce(AudioInitEvent.Reset)
        }
        return try {
            AudioContext().also { ctx ->
                audioContext = ctx
                installPageHide(ctx)
            }
        } catch (t: Throwable) {
            val message = "the AudioContext could not be created (${t.message ?: t})"
            failTerminal(message)
            null
        }
    }

    private fun resumeContext(ctx: AudioContext) {
        if (ctx.state == "suspended") ctx.resume().ignoreFailure("resume", hatchet)
    }

    private fun installPageHide(ctx: AudioContext) {
        if (pageHideInstalled) return
        pageHideInstalled = true
        // Release the AudioContext + worklet when the page goes away. `pagehide` fires reliably on
        // tab close, navigation, and the bf-cache transition; `beforeunload` would block on user
        // prompts in some configurations. Without this, the AudioContext + worklet thread leaks
        // until garbage collection picks them up (often never, for a closing tab).
        window.addEventListener("pagehide", { _ -> closeContext(ctx) })
    }

    private fun closeContext(ctx: AudioContext) {
        ctx.close().ignoreFailure("close", hatchet)
    }

    private companion object {
        const val WORKLET_URL = "/wasm/chipbox-audio-worklet.js"
        const val MILLIS_PER_SECOND = 1_000L
        const val SHORTS_PER_FRAME = 2

        /** One worklet init attempt (module fetch + node + resume). The retry gets its own budget. */
        const val INIT_TIMEOUT_MS = 4_000L

        /** Backstop around the full (initial + retry) preparation while the consume loop waits. */
        const val READY_WAIT_TIMEOUT_MS = 12_000L

        /** How long a full worklet queue may stay full before the sink is considered stalled. */
        const val CAPACITY_WAIT_TIMEOUT_MS = 10_000L

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

private suspend fun <T> Promise<T>.await(label: String): T = suspendCancellableCoroutine { cont ->
    then(
        { value ->
            cont.resume(value)
            undefined
        },
        { err ->
            cont.resumeWithException(RuntimeException("$label failed: $err"))
            undefined
        },
    )
}

/** Fire-and-forget promise whose rejection is logged instead of becoming an unhandled rejection. */
private fun Promise<*>.ignoreFailure(label: String, hatchet: Hatchet) {
    then(
        { undefined },
        { err ->
            hatchet.w("WebAudioSpeaker: $label was rejected: $err")
            undefined
        },
    )
}
