package net.sigmabeta.chipbox.browserstate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.serialization.Serializable

/**
 * The versioned envelope contract ticket 07 reuses: a `schemaVersion` header plus a typed payload.
 * Decode never throws — callers get Missing, Success (possibly migrated), or Failed and decide how
 * to surface the non-fatal message.
 */
class VersionedJsonCodecTest {

    @Serializable
    private data class Payload(
        val ids: List<Long> = emptyList(),
        val name: String = "untitled",
    )

    private val codec = VersionedJsonCodec(Payload.serializer(), currentVersion = 2)

    @Test
    fun `round-trips the current version and payload`() {
        val raw = codec.encode(Payload(ids = listOf(3L, 5L), name = "demo"))

        val success = codec.decode(raw).successOrFail()

        assertEquals(Payload(listOf(3L, 5L), "demo"), success.value)
        assertNull(success.migratedFrom, "current-version data needs no migration")
        assertTrue(raw.contains("\"schemaVersion\":2"), "envelope carries the schema version: $raw")
    }

    @Test
    fun `missing or blank data decodes as missing, not corrupt`() {
        assertTrue(codec.decode(null) is DecodeOutcome.Missing)
        assertTrue(codec.decode("") is DecodeOutcome.Missing)
        assertTrue(codec.decode("   ") is DecodeOutcome.Missing)
    }

    @Test
    fun `corrupt JSON falls back without throwing`() {
        val failure = codec.decode("{not json").failureOrFail()

        assertEquals(DecodeFailure.CORRUPT, failure.reason)
    }

    @Test
    fun `a missing payload or header is corrupt, not a crash`() {
        assertTrue(codec.decode("""{"payload":{"ids":[]}}""") is DecodeOutcome.Failed)
        assertTrue(codec.decode("""{"schemaVersion":2}""") is DecodeOutcome.Failed)
        assertTrue(codec.decode("""{"schemaVersion":"two","payload":{}}""") is DecodeOutcome.Failed)
    }

    @Test
    fun `an older version migrates when the payload still decodes`() {
        val raw = """{"schemaVersion":1,"payload":{"ids":[1,2],"name":"old"}}"""

        val success = codec.decode(raw).successOrFail()

        assertEquals(1, success.migratedFrom)
        assertEquals(Payload(listOf(1L, 2L), "old"), success.value)
    }

    @Test
    fun `an older version with an unreadable payload falls back as corrupt`() {
        val raw = """{"schemaVersion":1,"payload":{"ids":"not-a-list"}}"""

        val failure = codec.decode(raw).failureOrFail()

        assertEquals(DecodeFailure.CORRUPT, failure.reason)
    }

    @Test
    fun `a newer version is rejected without throwing`() {
        val raw = """{"schemaVersion":99,"payload":{"anything":"goes"}}"""

        val failure = codec.decode(raw).failureOrFail()

        assertEquals(DecodeFailure.UNSUPPORTED_VERSION, failure.reason)
    }

    private fun DecodeOutcome<Payload>.successOrFail(): DecodeOutcome.Success<Payload> =
        this as? DecodeOutcome.Success<Payload> ?: fail("expected success, was $this")

    private fun DecodeOutcome<Payload>.failureOrFail(): DecodeOutcome.Failed =
        this as? DecodeOutcome.Failed ?: fail("expected failure, was $this")
}
