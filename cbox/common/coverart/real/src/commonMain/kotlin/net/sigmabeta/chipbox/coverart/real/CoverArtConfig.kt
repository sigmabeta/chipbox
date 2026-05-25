package net.sigmabeta.chipbox.coverart.real

import net.sigmabeta.chipbox.coverart.IgdbCredentials
import net.sigmabeta.chipbox.coverart.parseIgdbCredentials
import okio.FileSystem
import okio.Path

/**
 * Reads/writes the IGDB credentials file. The INI parse itself is shared, pure logic in
 * coverart/api ([parseIgdbCredentials]); this just supplies the bytes from disk and can stamp out a
 * blank template:
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
    fun load(fileSystem: FileSystem, file: Path): IgdbCredentials? {
        if (fileSystem.metadataOrNull(file)?.isRegularFile != true) return null
        return parseIgdbCredentials(fileSystem.read(file) { readUtf8() }.lines())
    }

    /** Writes a commented template to [file] (only if it doesn't already exist) so the user can fill it in. */
    fun writeTemplate(fileSystem: FileSystem, file: Path) {
        if (fileSystem.exists(file)) return
        file.parent?.let { fileSystem.createDirectories(it) }
        fileSystem.write(file) {
            writeUtf8(
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
    }
}
