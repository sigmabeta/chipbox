package net.sigmabeta.chipbox.readers

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The scanner's only way to tell "a streamed-audio format this build can't read" from "an arbitrary
 * unknown file" once the native probe is gone. The set mirrors the vendored vgmstream build's
 * extensions (see [StreamedExtensions]), so a file with one of these extensions but no registered
 * reader is reported as skipped instead of vanishing.
 */
class StreamedExtensionsTest {

    @Test
    fun `recognises a streamed extension that has no Kotlin reader`() {
        // AT3/AA3 (ATRAC) is in the vendored build's list but outside the reader port's scope.
        assertTrue(isKnownStreamedExtension("aa3"))
        assertTrue(isKnownStreamedExtension("at3"))
    }

    @Test
    fun `matches case-insensitively`() {
        // File extensions reach the scanner lowercased, but the helper is public API and the JNI
        // list is lowercase, so accept either spelling rather than silently failing a caller.
        assertTrue(isKnownStreamedExtension("AA3"))
        assertTrue(isKnownStreamedExtension("Adx"))
    }

    @Test
    fun `still reports extensions that have a Kotlin reader`() {
        // The set mirrors vgmstream's build verbatim; deciding what to *do* with a known streamed
        // extension is the caller's job (the scanner dispatches registered readers first).
        assertTrue(isKnownStreamedExtension("adx"))
        assertTrue(isKnownStreamedExtension("brstm"))
    }

    @Test
    fun `does not claim extensions vgmstream's build excludes or never supported`() {
        // Common media types are subtracted from the vendored list, and container/metadata files
        // were never vgmstream formats — these must stay silent debug-level skips.
        assertFalse(isKnownStreamedExtension("wav"))
        assertFalse(isKnownStreamedExtension("ogg"))
        assertFalse(isKnownStreamedExtension("mp3"))
        assertFalse(isKnownStreamedExtension("flac"))
        assertFalse(isKnownStreamedExtension("txt"))
        assertFalse(isKnownStreamedExtension("gif"))
        assertFalse(isKnownStreamedExtension("jpg"))
        assertFalse(isKnownStreamedExtension("m3u"))
        assertFalse(isKnownStreamedExtension(""))
    }
}
