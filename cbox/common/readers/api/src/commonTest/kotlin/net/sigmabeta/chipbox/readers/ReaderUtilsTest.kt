package net.sigmabeta.chipbox.readers

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderUtilsTest {

    @Test
    fun `orUnknown returns Unknown for null`() {
        assertEquals(TAG_UNKNOWN, (null as String?).orUnknown())
    }

    @Test
    fun `orUnknown returns Unknown for empty string`() {
        assertEquals(TAG_UNKNOWN, "".orUnknown())
    }

    @Test
    fun `orUnknown returns Unknown for the PSF question-mark placeholder`() {
        // PSF tag conventions use "<?>" as a "no value" marker — surfacing it raw to the UI
        // would look like a real title, so it normalizes to "Unknown".
        assertEquals(TAG_UNKNOWN, "<?>".orUnknown())
    }

    @Test
    fun `orUnknown returns the original value when it is non-empty and not the placeholder`() {
        assertEquals("Final Fantasy", "Final Fantasy".orUnknown())
        // Whitespace is *not* trimmed — the placeholder check is exact-equality only.
        assertEquals(" ", " ".orUnknown())
    }

    @Test
    fun `toLengthMillis parses seconds-only`() {
        assertEquals(30_000L, "30".toLengthMillis())
    }

    @Test
    fun `toLengthMillis parses minutes and seconds`() {
        assertEquals(30_000L, "0:30".toLengthMillis())
        assertEquals(90_000L, "1:30".toLengthMillis())
        assertEquals(3_600_000L, "60:00".toLengthMillis())
    }

    @Test
    fun `toLengthMillis truncates fractional seconds`() {
        // Float fallback parses "30.5" via toFloat then casts to Int — the fractional second is
        // dropped before the *1000 ms scaling, so "30.5" and "1:00.5" both round down to a whole
        // second's worth of millis. Lock this in so a future change to honor sub-second precision
        // is a deliberate, visible test update.
        assertEquals(30_000L, "30.5".toLengthMillis())
        assertEquals(60_000L, "1:00.5".toLengthMillis())
    }

    @Test
    fun `toLengthMillis returns zero for unparseable input`() {
        // Float fallback returns 0f when toFloatOrNull is null — guards against a corrupt line
        // taking out the playlist.
        assertEquals(0L, "abc".toLengthMillis())
        assertEquals(0L, "".toLengthMillis())
    }

    @Test
    fun `toLengthMillis tolerates blank or non-numeric minutes without throwing`() {
        // Regression: a leading-colon or garbage minutes field (":30", "x:30") used to throw an
        // uncaught NumberFormatException and fail the whole file's scan. The minutes part now
        // contributes 0 instead.
        assertEquals(30_000L, ":30".toLengthMillis())
        assertEquals(30_000L, "x:30".toLengthMillis())
        assertEquals(0L, ":".toLengthMillis())
    }

    @Test
    fun `toLengthMillis returns zero for more than three colon-separated parts`() {
        // Unrecognized layout → 0; documented `else -> return 0L` branch.
        assertEquals(0L, "1:2:3:4".toLengthMillis())
    }

    @Test
    fun `toLengthMillis with H_M_S currently ignores the hours field`() {
        // Current behavior: for a three-part "1:00:30" the parser keeps minutes+seconds but
        // silently drops the hour. Pinning this in so a fix to honor hours is a deliberate,
        // visible test update rather than an accidental drift in downstream lengths.
        assertEquals(30_000L, "1:00:30".toLengthMillis())
    }
}
