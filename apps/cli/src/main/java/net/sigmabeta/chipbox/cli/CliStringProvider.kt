package net.sigmabeta.chipbox.cli

import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.ui.SageStringId
import net.sigmabeta.sage.ui.StringProvider
import java.util.Locale

/**
 * Minimal headless [StringProvider] for the CLI. The desktop/Android apps resolve strings from a
 * generated table / Android resources; the CLI only needs platform display names, so it carries
 * just the PLATFORM_* entries — mirrored from apps/jvm's `chipboxJvmStrings`, whose source of truth
 * is cbox/android/strings/api's strings XML. Unmapped ids fall back to the id's own string form.
 */
object CliStringProvider : StringProvider {
    private val strings: Map<SageStringId, String> = mapOf(
        ChipboxStringId.PLATFORM_ARCADE to "Arcade",
        ChipboxStringId.PLATFORM_DREAMCAST to "Dreamcast",
        ChipboxStringId.PLATFORM_GAMEBOY to "Game Boy",
        ChipboxStringId.PLATFORM_GAMEBOY_ADVANCE to "Game Boy Advance",
        ChipboxStringId.PLATFORM_GENESIS to "Genesis",
        ChipboxStringId.PLATFORM_N64 to "Nintendo 64",
        ChipboxStringId.PLATFORM_NDS to "Nintendo DS",
        ChipboxStringId.PLATFORM_NES to "NES",
        ChipboxStringId.PLATFORM_OTHER to "Other",
        ChipboxStringId.PLATFORM_PC to "PC",
        ChipboxStringId.PLATFORM_PS2 to "PlayStation 2",
        ChipboxStringId.PLATFORM_PSX to "PlayStation",
        ChipboxStringId.PLATFORM_SATURN to "Saturn",
        ChipboxStringId.PLATFORM_SNES to "SNES",
    )

    override fun getString(string: SageStringId): String = strings[string] ?: string.toString()

    override fun getStringOneArg(string: SageStringId, arg: String): String =
        getString(string).format(Locale.US, arg)

    override fun getStringOneInt(string: SageStringId, arg: Int): String =
        getString(string).format(Locale.US, arg)

    override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String =
        getString(string).format(Locale.US, first, second)
}
