package net.sigmabeta.chipbox.utils

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [resolveAppDataDir] is pure (every input is a parameter), so each OS/env branch is asserted
 * directly without touching the real environment. Expected paths are built with the same [File]
 * API as the implementation so the comparison stays separator-agnostic on whatever host runs the
 * test.
 */
class AppDataDirTest {

    private val home = "/home/user"
    private fun noEnv(@Suppress("UNUSED_PARAMETER") key: String): String? = null
    private fun envOf(vararg pairs: Pair<String, String>): (String) -> String? =
        { key -> pairs.toMap()[key] }

    @Test
    fun `linux without XDG_DATA_HOME falls back to local share`() {
        val dir = resolveAppDataDir("chipbox", "Chipbox", osName = "Linux", userHome = home, env = ::noEnv)
        assertEquals(File("$home/.local/share/chipbox"), dir)
    }

    @Test
    fun `linux honours an absolute XDG_DATA_HOME`() {
        val dir = resolveAppDataDir(
            "chipbox",
            "Chipbox",
            osName = "Linux",
            userHome = home,
            env = envOf("XDG_DATA_HOME" to "/custom/data"),
        )
        assertEquals(File("/custom/data/chipbox"), dir)
    }

    @Test
    fun `linux ignores a relative XDG_DATA_HOME`() {
        // The XDG spec requires relative values to be ignored; we fall back to ~/.local/share.
        val dir = resolveAppDataDir(
            "chipbox",
            "Chipbox",
            osName = "Linux",
            userHome = home,
            env = envOf("XDG_DATA_HOME" to "relative/path"),
        )
        assertEquals(File("$home/.local/share/chipbox"), dir)
    }

    @Test
    fun `the xdg leaf name is used on linux`() {
        val dir = resolveAppDataDir("chipbox-cli", "Chipbox CLI", osName = "Linux", userHome = home, env = ::noEnv)
        assertEquals(File("$home/.local/share/chipbox-cli"), dir)
    }

    @Test
    fun `macOS uses Application Support with the native name`() {
        val dir = resolveAppDataDir("chipbox", "Chipbox", osName = "Mac OS X", userHome = home, env = ::noEnv)
        assertEquals(File("$home/Library/Application Support/Chipbox"), dir)
    }

    @Test
    fun `windows prefers LOCALAPPDATA`() {
        val dir = resolveAppDataDir(
            "chipbox",
            "Chipbox",
            osName = "Windows 11",
            userHome = home,
            env = envOf("LOCALAPPDATA" to "C:\\Users\\u\\AppData\\Local", "APPDATA" to "C:\\Users\\u\\AppData\\Roaming"),
        )
        assertEquals(File("C:\\Users\\u\\AppData\\Local", "Chipbox"), dir)
    }

    @Test
    fun `windows falls back to APPDATA then to the home AppData Local`() {
        val viaAppData = resolveAppDataDir(
            "chipbox",
            "Chipbox",
            osName = "Windows 10",
            userHome = home,
            env = envOf("APPDATA" to "C:\\Users\\u\\AppData\\Roaming"),
        )
        assertEquals(File("C:\\Users\\u\\AppData\\Roaming", "Chipbox"), viaAppData)

        val viaHome = resolveAppDataDir("chipbox", "Chipbox", osName = "Windows 10", userHome = home, env = ::noEnv)
        assertEquals(File(File(home, "AppData/Local").path, "Chipbox"), viaHome)
    }
}
