package net.sigmabeta.chipbox.common.ui.components.api.previews

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import net.sigmabeta.chipbox.strings.api.LocalChipboxStringProvider
import net.sigmabeta.chipbox.strings.real.rememberChipboxStringProvider
import net.sigmabeta.chipbox.ui.theme.api.AppTheme

@Composable
fun ChipboxPreview(
    forceDark: Boolean = false,
    content: @Composable () -> Unit,
) = AppTheme(darkTheme = forceDark) {
    WithPreviewStringProvider(content)
}

/**
 * Installs [LocalChipboxStringProvider] for previews. Composables swapped to read the local in
 * M9 slice 6c (e.g. `LabelValueListItem`'s `ACCY_OCL_VALUE` a11y description) crash without it —
 * `MainActivity` / `DesktopMain` provide it at runtime, so previews must too. Backed by the single
 * multiplatform string source via [rememberChipboxStringProvider], so the text matches the app.
 */
@Composable
private fun WithPreviewStringProvider(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalChipboxStringProvider provides rememberChipboxStringProvider()) {
        content()
    }
}
