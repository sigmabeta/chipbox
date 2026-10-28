package net.sigmabeta.chipbox.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Standard error body for every non-2xx `/api` response: `{"error": "..."}`.
 *
 * Shared by the server (producer) and the browser client (consumer) so the wire shape and its
 * decoding live in one place instead of duplicated string literals.
 */
@Serializable
data class ErrorResponse(val error: String)

/** Error bodies may grow fields over time; clients must ignore anything they don't know. */
private val errorJson = Json { ignoreUnknownKeys = true }

/**
 * Classifies an HTTP response before the browser decodes it as JSON.
 *
 * Returns `null` when the response is a JSON success (`2xx`), otherwise the message the UI should
 * show. A non-2xx body is decoded as [ErrorResponse] when possible, so the server's own explanation
 * ("Unknown track 7") reaches the user instead of a deserialization failure. A `2xx` that is not
 * JSON (the SPA fallback's `text/html`, for example) is also an error: the client asked an API
 * endpoint for data and got a web page.
 *
 * Pure and platform-free, so both the Ktor response path and the Node tests can exercise it.
 */
fun apiErrorMessage(status: Int, contentType: String?, body: String?): String? {
    // Content-Type carries a charset suffix in practice (`application/json; charset=UTF-8`).
    val mediaType = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
    val isJson = mediaType.isEmpty() || mediaType == "application/json" || mediaType.endsWith("+json")

    if (status in 200..299) {
        if (isJson) return null
        return "The server returned ${mediaType.ifEmpty { "an unknown content type" }} instead of " +
            "JSON (HTTP $status). Is the API server running at this address?"
    }

    val serverMessage = body
        ?.let { runCatching { errorJson.decodeFromString<ErrorResponse>(it).error }.getOrNull() }
        ?.takeIf { it.isNotBlank() }
    if (serverMessage != null) return serverMessage

    return if (mediaType.contains("html")) {
        "HTTP $status: the server returned an HTML page instead of an API error."
    } else {
        "HTTP $status: the server rejected the request."
    }
}
