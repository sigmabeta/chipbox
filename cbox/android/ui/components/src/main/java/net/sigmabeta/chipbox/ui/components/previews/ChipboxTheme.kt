package net.sigmabeta.chipbox.ui.components.previews

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun ChipboxTheme(
    @Suppress("UNUSED_PARAMETER") forceDark: Boolean = false,
    content: @Composable () -> Unit,
) = MaterialTheme(content = content)

@Composable
fun ChipboxThemeMenu(content: @Composable () -> Unit) = MaterialTheme(content = content)
