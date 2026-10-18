package net.sigmabeta.chipbox.player.speaker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The browser's AudioWorklet initialization is a four-phase lifecycle with one retry. It lives
 * here (pure, no Promise/Browser APIs) so the tricky part — never latching a failed init forever,
 * never replaying a successful one — is testable under Node as well as the JVM.
 */
class AudioInitStateTest {

    @Test
    fun `prepare moves an idle sink into preparing`() {
        val next = AudioInitState().reduce(AudioInitEvent.Prepare)

        assertEquals(AudioInitPhase.PREPARING, next.phase)
        assertEquals(0, next.attempts)
        assertNull(next.message)
    }

    @Test
    fun `a successful preparation is ready`() {
        val next = AudioInitState()
            .reduce(AudioInitEvent.Prepare)
            .reduce(AudioInitEvent.Succeeded)

        assertEquals(AudioInitPhase.READY, next.phase)
        assertTrue(next.isReady)
    }

    @Test
    fun `prepare is idempotent while preparing and once ready`() {
        val preparing = AudioInitState().reduce(AudioInitEvent.Prepare)
        assertEquals(preparing, preparing.reduce(AudioInitEvent.Prepare))

        val ready = preparing.reduce(AudioInitEvent.Succeeded)
        assertEquals(ready, ready.reduce(AudioInitEvent.Prepare), "an unlocked sink must not be rebuilt")
    }

    @Test
    fun `a failure is retryable once and then terminal`() {
        val firstFailure = AudioInitState()
            .reduce(AudioInitEvent.Prepare)
            .reduce(AudioInitEvent.Failed("addModule rejected"))

        assertEquals(AudioInitPhase.FAILED, firstFailure.phase)
        assertEquals(1, firstFailure.attempts)
        assertTrue(firstFailure.mayRetry, "one retry is still allowed")
        assertFalse(firstFailure.isTerminalFailure)

        val retry = firstFailure.reduce(AudioInitEvent.Prepare)
        assertEquals(AudioInitPhase.PREPARING, retry.phase)
        assertNull(retry.message, "a retry clears the previous failure message")
        assertEquals(1, retry.attempts, "the failed attempt is remembered across the retry")

        val secondFailure = retry.reduce(AudioInitEvent.Failed("node creation failed"))
        assertEquals(AudioInitPhase.FAILED, secondFailure.phase)
        assertEquals(2, secondFailure.attempts)
        assertTrue(secondFailure.isTerminalFailure, "no more retries after the second failure")
        assertEquals("node creation failed", secondFailure.message)

        assertEquals(
            secondFailure,
            secondFailure.reduce(AudioInitEvent.Prepare),
            "prepare must not start a third attempt",
        )
    }

    @Test
    fun `a successful retry becomes ready and clears the attempt count`() {
        val next = AudioInitState()
            .reduce(AudioInitEvent.Prepare)
            .reduce(AudioInitEvent.Failed("first attempt failed"))
            .reduce(AudioInitEvent.Prepare)
            .reduce(AudioInitEvent.Succeeded)

        assertEquals(AudioInitPhase.READY, next.phase)
        assertEquals(0, next.attempts)
        assertNull(next.message)
    }

    @Test
    fun `a failure outside preparing is ignored`() {
        val idle = AudioInitState()
        assertEquals(idle, idle.reduce(AudioInitEvent.Failed("stray")))

        val ready = idle.reduce(AudioInitEvent.Prepare).reduce(AudioInitEvent.Succeeded)
        assertEquals(ready, ready.reduce(AudioInitEvent.Failed("stray")))
    }

    @Test
    fun `reset returns to idle so the next play can rebuild`() {
        val failed = AudioInitState()
            .reduce(AudioInitEvent.Prepare)
            .reduce(AudioInitEvent.Failed("boom"))
            .reduce(AudioInitEvent.Prepare)
            .reduce(AudioInitEvent.Failed("boom again"))

        assertEquals(AudioInitState(), failed.reduce(AudioInitEvent.Reset))
    }
}
