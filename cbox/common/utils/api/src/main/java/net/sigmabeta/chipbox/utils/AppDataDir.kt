package net.sigmabeta.chipbox.utils

import java.io.File
import java.util.Locale

/**
 * The directory a Chipbox JVM app (desktop, CLI, …) stores its data in, resolved from the live
 * JVM/OS environment. Thin wrapper over [resolveAppDataDir] that reads `os.name`, `user.home`,
 * and the relevant environment variables; the resolution itself is kept pure so it can be
 * reasoned about (and tested) without touching real system state.
 *
 * @param xdgName the directory leaf used on Linux/other (lowercase, e.g. `chipbox`, `chipbox-cli`).
 * @param nativeName the directory leaf used on macOS/Windows, where convention favours a
 *        human-readable name (e.g. `Chipbox`, `Chipbox CLI`).
 */
fun appDataDir(xdgName: String, nativeName: String): File = resolveAppDataDir(
    xdgName = xdgName,
    nativeName = nativeName,
    osName = System.getProperty("os.name").orEmpty(),
    userHome = System.getProperty("user.home").orEmpty(),
    env = System::getenv,
)

/**
 * Resolve a Chipbox app's data directory following each OS's convention:
 *  - **Windows** → `%LOCALAPPDATA%\<nativeName>` (falling back to `%APPDATA%`, then
 *    `<user.home>\AppData\Local`).
 *  - **macOS** → `<user.home>/Library/Application Support/<nativeName>`.
 *  - **Linux / other** → `$XDG_DATA_HOME/<xdgName>` when `XDG_DATA_HOME` is set to an absolute
 *    path, else `<user.home>/.local/share/<xdgName>`.
 *
 * Pure: every input comes through the parameters, so the same arguments always produce the same
 * path. [env] is the environment-variable lookup (e.g. `System::getenv`).
 */
fun resolveAppDataDir(
    xdgName: String,
    nativeName: String,
    osName: String,
    userHome: String,
    env: (String) -> String?,
): File {
    val home = File(userHome)
    val os = osName.lowercase(Locale.ROOT)
    return when {
        os.startsWith("windows") -> {
            val base = env("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
                ?: env("APPDATA")?.takeIf { it.isNotBlank() }
                ?: File(home, "AppData/Local").path
            File(base, nativeName)
        }

        os.startsWith("mac") || os.startsWith("darwin") ->
            File(home, "Library/Application Support/$nativeName")

        else -> {
            // XDG Base Directory spec: only honour XDG_DATA_HOME when it's an absolute path
            // (relative values are required to be ignored), otherwise default to ~/.local/share.
            val dataHome = env("XDG_DATA_HOME")
                ?.takeIf { it.isNotBlank() && File(it).isAbsolute }
                ?: File(home, ".local/share").path
            File(dataHome, xdgName)
        }
    }
}
