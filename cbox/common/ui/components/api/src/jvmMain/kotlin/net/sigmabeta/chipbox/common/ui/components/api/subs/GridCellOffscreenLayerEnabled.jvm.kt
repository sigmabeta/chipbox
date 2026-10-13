package net.sigmabeta.chipbox.common.ui.components.api.subs

/**
 * JVM (Compose Desktop, Skiko): keep the per-cell offscreen layer. Skiko otherwise
 * re-rasterizes a translated cell every frame; caching it turns grid scroll into a cheap
 * translate. See [gridCellOffscreenLayerEnabled].
 */
internal actual val gridCellOffscreenLayerEnabled: Boolean = true
