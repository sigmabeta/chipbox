package net.sigmabeta.chipbox.ui.fonts.real

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import net.sigmabeta.chipbox.common.ui.fonts.real.generated.resources.Res
import net.sigmabeta.chipbox.common.ui.fonts.real.generated.resources.earthbound
import net.sigmabeta.chipbox.common.ui.fonts.real.generated.resources.enix
import net.sigmabeta.chipbox.common.ui.fonts.real.generated.resources.final_fantasy_iv
import net.sigmabeta.chipbox.common.ui.fonts.real.generated.resources.final_fantasy_vi
import net.sigmabeta.chipbox.common.ui.fonts.real.generated.resources.final_fantasy_vii
import net.sigmabeta.chipbox.common.ui.fonts.real.generated.resources.mega_man_x
import net.sigmabeta.chipbox.common.ui.fonts.real.generated.resources.nds
import net.sigmabeta.chipbox.common.ui.fonts.real.generated.resources.nes
import net.sigmabeta.chipbox.common.ui.fonts.real.generated.resources.open_dyslexic
import net.sigmabeta.chipbox.common.ui.fonts.real.generated.resources.ps4
import net.sigmabeta.chipbox.common.ui.fonts.real.generated.resources.shining_force_lg
import net.sigmabeta.chipbox.common.ui.fonts.real.generated.resources.shining_force_sm
import net.sigmabeta.chipbox.common.ui.fonts.real.generated.resources.shinobi
import net.sigmabeta.chipbox.common.ui.fonts.real.generated.resources.sonic1
import net.sigmabeta.chipbox.common.ui.fonts.real.generated.resources.sonic3
import net.sigmabeta.chipbox.common.ui.fonts.real.generated.resources.star_fox_64
import net.sigmabeta.chipbox.common.ui.fonts.real.generated.resources.super_mario_64
import net.sigmabeta.chipbox.common.ui.fonts.real.generated.resources.tloz_lttp
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.FontResource
import net.sigmabeta.chipbox.ui.fonts.ChipboxFont

/**
 * Compose binding for [ChipboxFont]. Lives in `:real` so the `:api` enum stays Compose-free —
 * meaningful on Kotlin/JS, where the Compose-Resources runtime loads Skiko at class-init time
 * (which doesn't resolve under Node). Consumers that just carry a `ChipboxFont` through
 * actions / state can depend on `:api`; only modules that actually render with the font (e.g.
 * `cbox/common/ui/theme/api/AppTheme`) need `:real`.
 */
val ChipboxFont.resource: FontResource
    get() = when (this) {
        ChipboxFont.PLANETARY -> Res.font.earthbound
        ChipboxFont.METEOR -> Res.font.final_fantasy_vii
        ChipboxFont.ESPERANTO -> Res.font.final_fantasy_vi
        ChipboxFont.BARON -> Res.font.final_fantasy_iv
        ChipboxFont.UNSQUARED -> Res.font.enix
        ChipboxFont.POWERFUL -> Res.font.super_mario_64
        ChipboxFont.ENERGY_MITT -> Res.font.nes
        ChipboxFont.DOUBLE_TOUCH -> Res.font.nds
        ChipboxFont.MAVERICK -> Res.font.mega_man_x
        ChipboxFont.THREE_POWERS -> Res.font.tloz_lttp
        ChipboxFont.SPACE_PETS -> Res.font.star_fox_64
        ChipboxFont.BRILLIANT -> Res.font.shining_force_lg
        ChipboxFont.REBEL -> Res.font.shining_force_sm
        ChipboxFont.LANDEEL -> Res.font.ps4
        ChipboxFont.NINJA -> Res.font.shinobi
        ChipboxFont.ECHIDNA -> Res.font.sonic3
        ChipboxFont.PORCUPINE -> Res.font.sonic1
        ChipboxFont.DYSLEXIC -> Res.font.open_dyslexic
    }

/**
 * Multiplatform `FontFamily` for [this]. CMP's resources codegen produces the appropriate
 * per-target [Font] (Android: packaged into the AAR's `composeResources`; JVM/desktop:
 * bundled on the classpath under `composeResources/font/`).
 *
 * `@Composable` because the underlying CMP `Font(FontResource)` factory is — font loading
 * happens lazily and is bound to the composition's lifecycle.
 */
@Composable
fun ChipboxFont.toFontFamily(): FontFamily = FontFamily(Font(resource))
