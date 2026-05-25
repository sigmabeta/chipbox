package net.sigmabeta.chipbox.cli

import kotlinx.coroutines.runBlocking
import net.sigmabeta.chipbox.strings.real.ChipboxStringProvider
import net.sigmabeta.chipbox.strings.real.loadChipboxStrings
import net.sigmabeta.sage.ui.StringProvider

/**
 * The CLI's [StringProvider], backed by the single multiplatform string source
 * (cbox/common/strings/real's composeResources) — the same values the Android/desktop apps resolve,
 * instead of the old hand-maintained PLATFORM_* subset. Loaded once, lazily, since the menus only
 * touch it to render platform / section labels.
 */
val cliStringProvider: StringProvider by lazy { runBlocking { ChipboxStringProvider(loadChipboxStrings()) } }
