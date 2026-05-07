package net.sigmabeta.chipbox.ui.components.previews

import androidx.compose.runtime.Composable
import net.sigmabeta.chipbox.ui.theme.AppTheme
import net.sigmabeta.chipbox.ui.theme.AppThemeMenu

@Composable
fun ChipboxTheme(
    forceDark: Boolean = false,
    content: @Composable () -> Unit,
) = AppTheme(forceDark = forceDark, content = content)

@Composable
fun ChipboxThemeMenu(content: @Composable () -> Unit) = AppThemeMenu(content = content)
