package net.sigmabeta.chipbox.ui.components.api.previews

import androidx.compose.runtime.Composable
import net.sigmabeta.chipbox.ui.theme.api.AppTheme
import net.sigmabeta.chipbox.ui.theme.api.AppThemeMenu

@Composable
fun ChipboxPreview(
    forceDark: Boolean = false,
    content: @Composable () -> Unit,
) = AppTheme(forceDark = forceDark, content = content)

@Composable
fun ChipboxPreviewMenu(content: @Composable () -> Unit) = AppThemeMenu(content = content)
