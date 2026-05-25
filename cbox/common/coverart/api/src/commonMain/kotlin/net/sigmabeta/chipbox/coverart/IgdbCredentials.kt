package net.sigmabeta.chipbox.coverart

/** IGDB (via Twitch) API credentials used to search for cover art. */
data class IgdbCredentials(
    val clientId: String,
    val clientSecret: String,
)

/**
 * Parses IGDB credentials from the lines of a tiny INI-style config:
 *
 * ```
 * [igdb]
 * client_id = ...
 * client_secret = ...
 * ```
 *
 * Returns the credentials only if both keys are present and non-blank under the `[igdb]` section,
 * else null. Blank lines and `#` comments are ignored. (Register an application at
 * https://dev.twitch.tv to obtain the client id / secret.) Pure so every app shares the same
 * parsing; reading the file from disk is the caller's job (see `CoverArtConfig` in coverart/real).
 */
fun parseIgdbCredentials(lines: List<String>): IgdbCredentials? {
    val values = mutableMapOf<String, String>()
    var inIgdbSection = false
    for (raw in lines) {
        val line = raw.substringBefore('#').trim()
        when {
            line.isEmpty() -> Unit

            line.startsWith("[") && line.endsWith("]") ->
                inIgdbSection = line.substring(1, line.length - 1).trim().equals("igdb", ignoreCase = true)

            inIgdbSection && '=' in line ->
                values[line.substringBefore('=').trim()] = line.substringAfter('=').trim()
        }
    }
    val clientId = values["client_id"]?.takeIf { it.isNotBlank() }
    val clientSecret = values["client_secret"]?.takeIf { it.isNotBlank() }
    return if (clientId != null && clientSecret != null) IgdbCredentials(clientId, clientSecret) else null
}
