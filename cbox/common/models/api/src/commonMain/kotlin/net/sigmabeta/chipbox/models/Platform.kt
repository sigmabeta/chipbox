package net.sigmabeta.chipbox.models

import kotlinx.serialization.Serializable
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.ui.SageStringId

// Enum serializes by name (e.g. "NES"); the SageStringId constructor arg is metadata, ignored.
@Serializable
enum class Platform(val stringId: SageStringId) {
    ARCADE(ChipboxStringId.PLATFORM_ARCADE),
    DREAMCAST(ChipboxStringId.PLATFORM_DREAMCAST),
    GAMEBOY(ChipboxStringId.PLATFORM_GAMEBOY),
    GAMEBOY_ADVANCE(ChipboxStringId.PLATFORM_GAMEBOY_ADVANCE),
    GENESIS(ChipboxStringId.PLATFORM_GENESIS),
    NES(ChipboxStringId.PLATFORM_NES),
    N64(ChipboxStringId.PLATFORM_N64),
    NDS(ChipboxStringId.PLATFORM_NDS),
    PC(ChipboxStringId.PLATFORM_PC),
    PS2(ChipboxStringId.PLATFORM_PS2),
    PSX(ChipboxStringId.PLATFORM_PSX),
    SATURN(ChipboxStringId.PLATFORM_SATURN),
    SNES(ChipboxStringId.PLATFORM_SNES),
    OTHER(ChipboxStringId.PLATFORM_OTHER),
}
