package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.sigmabeta.sage.images.SourceInfo

/**
 * Chipbox's inspection-mode stand-in art, wired into [ChipboxRenderers]'s `previewImage` hook.
 *
 * SAGE's `FakeImage` draws a deterministic gradient by default; on Android Chipbox keeps its
 * historical `BitmapGenerator` art (so recorded Paparazzi goldens stay byte-identical), while the
 * JVM and JS targets leave it `null` to use SAGE's default.
 */
internal expect val chipboxPreviewImage: (@Composable (SourceInfo, Modifier) -> Unit)?
