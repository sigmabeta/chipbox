package net.sigmabeta.chipbox.player.speaker

/**
 * Lifecycle of an asynchronous audio sink's initialization.
 *
 * Most sinks ([net.sigmabeta.chipbox.player.speaker.real.RealSpeaker]'s `AudioTrack`, the JVM
 * `SourceDataLine`) are ready the moment they open. The browser's `AudioWorklet` is not: the
 * module loads behind a Promise and the context may stay suspended until a user gesture. This
 * state machine makes that async startup explicit so a failure can never latch the sink as
 * "initialized" while no node exists, and so an end-of-session teardown can drop back to a state
 * the next [Speaker.play] can rebuild from.
 *
 * At most [AudioInitState.MAX_ATTEMPTS] attempts are made (the initial one plus one retry). A
 * retry is expressed by reducing another [AudioInitEvent.Prepare] from [AudioInitPhase.FAILED]
 * while [AudioInitState.mayRetry] is true; the speaker's driver owns the actual scheduling.
 */
data class AudioInitState(
    val phase: AudioInitPhase = AudioInitPhase.IDLE,
    /** Failed attempts so far. Reset once the sink is ready, kept across a retry. */
    val attempts: Int = 0,
    /** Human-readable reason for [AudioInitPhase.FAILED]; null in every other phase. */
    val message: String? = null,
) {
    val isReady: Boolean get() = phase == AudioInitPhase.READY

    /** A failed state from which the driver may schedule exactly one more attempt. */
    val mayRetry: Boolean
        get() = phase == AudioInitPhase.FAILED && attempts < MAX_ATTEMPTS

    /** The final failure — no further attempt is allowed; surface it to the user. */
    val isTerminalFailure: Boolean
        get() = phase == AudioInitPhase.FAILED && attempts >= MAX_ATTEMPTS

    companion object {
        /** One initial attempt plus one retry. */
        const val MAX_ATTEMPTS = 2
    }
}

enum class AudioInitPhase { IDLE, PREPARING, READY, FAILED }

sealed interface AudioInitEvent {
    /** The user asked for audio (a play gesture, or the consume loop reaching the sink). */
    data object Prepare : AudioInitEvent

    /** `addModule` + node creation + `resume` all completed. */
    data object Succeeded : AudioInitEvent

    /** This attempt failed (rejected promise, timeout, missing worklet, …). */
    data class Failed(val message: String) : AudioInitEvent

    /** Drop back to idle — the sink or context is gone and must be rebuilt from scratch. */
    data object Reset : AudioInitEvent
}

fun AudioInitState.reduce(event: AudioInitEvent): AudioInitState = when (event) {
    AudioInitEvent.Prepare -> when (phase) {
        AudioInitPhase.IDLE -> copy(phase = AudioInitPhase.PREPARING, attempts = 0, message = null)

        // A ready sink is already unlocked — rebuilding it would re-trigger the gesture gate.
        AudioInitPhase.READY, AudioInitPhase.PREPARING -> this

        AudioInitPhase.FAILED ->
            if (mayRetry) copy(phase = AudioInitPhase.PREPARING, message = null) else this
    }

    AudioInitEvent.Succeeded ->
        if (phase == AudioInitPhase.PREPARING) {
            copy(phase = AudioInitPhase.READY, attempts = 0, message = null)
        } else {
            this
        }

    is AudioInitEvent.Failed ->
        if (phase == AudioInitPhase.PREPARING) {
            copy(phase = AudioInitPhase.FAILED, attempts = attempts + 1, message = event.message)
        } else {
            this
        }

    AudioInitEvent.Reset -> AudioInitState()
}
