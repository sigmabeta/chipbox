package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.sigmabeta.sage.images.SourceInfo

/** JS actual: fall back to SAGE's deterministic gradient stand-in. */
internal actual val chipboxPreviewImage: (@Composable (SourceInfo, Modifier) -> Unit)? = null
