package net.sigmabeta.chipbox.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

/**
 * The browser client must turn a failed API response into the message the UI shows. Before this,
 * a non-JSON body (the SPA's `index.html`, a proxy error page) reached the JSON decoder and the
 * user saw "Unexpected JSON token at offset 0" instead of "Not found".
 *
 * The classification is pure, so it is tested here (both JVM and Node) rather than through a
 * browser.
 */
class ApiErrorMessageTest {

    @Test
    fun `error response serializes with the standard field name`() {
        assertEquals("""{"error":"Not found"}""", Json.encodeToString(ErrorResponse("Not found")))
    }

    @Test
    fun `json success is not an error`() {
        assertNull(apiErrorMessage(200, "application/json", "[]"))
    }

    @Test
    fun `json success with a charset parameter is not an error`() {
        assertNull(apiErrorMessage(200, "application/json; charset=UTF-8", """{"id":1}"""))
    }

    @Test
    fun `server error body becomes the message`() {
        assertEquals(
            "Not found",
            apiErrorMessage(404, "application/json", """{"error":"Not found"}"""),
        )
    }

    @Test
    fun `unknown fields in an error body are ignored`() {
        assertEquals(
            "Unknown track 7",
            apiErrorMessage(404, "application/json", """{"error":"Unknown track 7","detail":"extra"}"""),
        )
    }

    @Test
    fun `an html success is reported as a non-json response`() {
        val message = assertNotNull(apiErrorMessage(200, "text/html; charset=UTF-8", "<html></html>"))

        assertTrue("text/html" in message, "message should name the content type: $message")
        assertTrue("200" in message, "message should name the status: $message")
    }

    @Test
    fun `an html error page gets a clear message`() {
        val message = assertNotNull(apiErrorMessage(404, "text/html", "<html>Not Found</html>"))

        assertTrue("HTTP 404" in message, "message should name the status: $message")
        assertTrue("HTML" in message, "message should say the body was HTML: $message")
    }

    @Test
    fun `an error without a decodable body falls back to the status`() {
        val message = assertNotNull(apiErrorMessage(500, "text/plain", "upstream exploded"))

        assertEquals("HTTP 500: the server rejected the request.", message)
    }

    @Test
    fun `a json body without an error field falls back to the status`() {
        val message = assertNotNull(apiErrorMessage(400, "application/json", """{"message":"nope"}"""))

        assertEquals("HTTP 400: the server rejected the request.", message)
    }
}
