package net.sigmabeta.chipbox.cli

import java.io.File

/** IGDB (via Twitch) API credentials used to search for cover art. */
data class IgdbCredentials(
    val clientId: String,
    val clientSecret: String,
)

/**
 * Reads IGDB credentials from a tiny INI-style config file:
 *
 * ```
 * [igdb]
 * client_id = ...
 * client_secret = ...
 * ```
 *
 * Register an application at https://dev.twitch.tv to obtain the client ID / secret.
 */
object CoverArtConfig {
    /** Returns the credentials if the file exists and both keys are present and non-blank, else null. */
    fun load(file: File): IgdbCredentials? {
        if (!file.isFile) return null
        val values = parse(file.readLines())
        val clientId = values["client_id"]?.takeIf { it.isNotBlank() }
        val clientSecret = values["client_secret"]?.takeIf { it.isNotBlank() }
        return if (clientId != null && clientSecret != null) IgdbCredentials(clientId, clientSecret) else null
    }

    /** Writes a commented template to [file] (only if it doesn't already exist) so the user can fill it in. */
    fun writeTemplate(file: File) {
        if (file.exists()) return
        file.parentFile?.mkdirs()
        file.writeText(
            """
            |# Chipbox cover-art credentials.
            |# Register an application at https://dev.twitch.tv to obtain these, then fill them in.
            |[igdb]
            |client_id =
            |client_secret =
            |
            """.trimMargin(),
        )
    }

    // Flat key=value parse, scoped to the [igdb] section. Blank lines and `#` comments are ignored.
    private fun parse(lines: List<String>): Map<String, String> {
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
        return values
    }
}
