package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import net.sigmabeta.chipbox.strings.api.LocalChipboxStringProvider
import net.sigmabeta.sage.ui.StringProvider
import net.sigmabeta.sage.ui.composables.ProvideSageRenderers

/**
 * Installs the two string/renderer locals the shared renderer suite reads, around [content]:
 * [LocalChipboxStringProvider] (chipbox's own) and SAGE's `LocalSageStringProvider` +
 * `LocalSageRenderers` (via [ProvideSageRenderers], wired to [ChipboxRenderers]).
 *
 * Every host that already installed `LocalChipboxStringProvider` (the app entry points, the
 * Paparazzi `ScreenPreview`s, and the UI-test harnesses) calls this instead, so the SAGE renderers
 * resolve chipbox's accessibility labels and the Chipbox rows plug in through one call.
 */
@Composable
fun ProvideChipboxText(
    stringProvider: StringProvider,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalChipboxStringProvider provides stringProvider) {
        ProvideSageRenderers(stringProvider = stringProvider, renderers = ChipboxRenderers) {
            content()
        }
    }
}
