package net.sigmabeta.chipbox.common.ui.components.api.subs

/**
 * Android: no per-cell offscreen layer. The cached layer costs an extra GPU surface per
 * visible cell while Android's hardware renderer already caches each cell's `RenderNode`, so
 * grid scroll gains nothing but pays the surface / graphics-memory cost. See
 * [gridCellOffscreenLayerEnabled].
 */
internal actual val gridCellOffscreenLayerEnabled: Boolean = false
