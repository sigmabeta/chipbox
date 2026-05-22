package net.sigmabeta.chipbox.common.ui.components.api.previews

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.chipbox.strings.api.LocalChipboxStringProvider
import net.sigmabeta.chipbox.strings.api.id
import net.sigmabeta.chipbox.ui.theme.api.AppTheme
import net.sigmabeta.sage.ui.strings.AndroidStringProvider

@Composable
fun ChipboxPreview(
    forceDark: Boolean = false,
    content: @Composable () -> Unit,
) = AppTheme(forceDark = forceDark) {
    WithPreviewStringProvider(content)
}

/**
 * Installs [LocalChipboxStringProvider] for previews. Composables swapped to read the local in
 * M9 slice 6c (e.g. `LabelValueListItem`'s `ACCY_OCL_VALUE` a11y description) crash without it —
 * `MainActivity` / `DesktopMain` provide it at runtime, so previews must too. Android-resource-
 * backed so the resolved text matches the running app.
 */
@Composable
private fun WithPreviewStringProvider(content: @Composable () -> Unit) {
    val stringProvider =
        AndroidStringProvider(LocalContext.current.resources) { (it as ChipboxStringId).id() }
    CompositionLocalProvider(LocalChipboxStringProvider provides stringProvider) {
        content()
    }
}
