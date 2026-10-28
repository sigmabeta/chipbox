package net.sigmabeta.chipbox.readers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import net.sigmabeta.sage.logging.BluntHatchet

/**
 * The version gate reads its current per-extension version from the production registry. These
 * cases pin the cutover semantics: a streamed extension that *gained* a Kotlin reader moves from
 * the no-reader sentinel (0) to a real version, so every folder holding one re-reads; an extension
 * that is still unregistered keeps comparing 0 == 0 and stays skippable.
 */
class ReadersVersionCutoverTest {

    private val readers = Readers(BluntHatchet())

    @Test
    fun `a streamed extension with a Kotlin reader now reports a real version`() {
        // Before the cutover every ADX track was stamped reader_version = 0 by the native probe.
        assertNotEquals(Readers.NO_READER_VERSION, readers.readerVersionFor("adx"))
    }

    @Test
    fun `every in-scope streamed format is registered`() {
        listOf("adx", "hca", "brstm", "bfstm", "bcstm", "dsp", "fsb5", "awb", "acb", "aix", "vag")
            .forEach { extension ->
                assertTrue(
                    readers.forExtension(extension) != null,
                    "expected a registered reader for .$extension",
                )
            }
    }

    @Test
    fun `an out-of-scope streamed extension stays at the no-reader sentinel`() {
        assertTrue(isKnownStreamedExtension("at3"))
        assertEquals(Readers.NO_READER_VERSION, readers.readerVersionFor("at3"))
    }
}
