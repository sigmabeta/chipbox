package net.sigmabeta.chipbox.contentsource

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ContentSourceRegistryTest {

    private class FakeSource(override val sourceId: String) : ContentSource {
        // Registry tests only inspect the id index; openBytes is irrelevant here.
        override suspend fun openBytes(identifier: String): ByteArray? = null
    }

    @Test
    fun `get returns the source registered under the matching id`() {
        val file = FakeSource("file")
        val saf = FakeSource("saf")
        val registry = ContentSourceRegistry(setOf(file, saf))

        // Use assertSame — the registry must hand back the original instance, not a copy.
        assertSame(file, registry.get("file"))
        assertSame(saf, registry.get("saf"))
    }

    @Test
    fun `get returns null for an unknown id`() {
        val registry = ContentSourceRegistry(setOf(FakeSource("file")))
        assertNull(registry.get("nope"))
    }

    @Test
    fun `empty source set always returns null`() {
        val registry = ContentSourceRegistry(emptySet())
        assertNull(registry.get("anything"))
    }

    @Test
    fun `id lookup is case-sensitive`() {
        // Source ids are configuration keys, not user input — a case-insensitive match would hide
        // typos in DI wiring.
        val registry = ContentSourceRegistry(setOf(FakeSource("File")))
        assertNull(registry.get("file"))
        assertEquals("File", registry.get("File")?.sourceId)
    }
}
