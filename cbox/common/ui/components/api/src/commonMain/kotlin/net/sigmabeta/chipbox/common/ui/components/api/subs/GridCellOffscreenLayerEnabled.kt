package net.sigmabeta.chipbox.common.ui.components.api.subs

/**
 * Platform gate for the per-cell offscreen compositing layer in
 * [net.sigmabeta.chipbox.common.ui.components.api.GridImage].
 *
 * The layer (`CompositingStrategy.Offscreen`) caches a cell's rasterized contents so that
 * scroll translates the cached layer instead of re-rasterizing the image + scrim + text on
 * every frame. That is a real win on Skiko (web + JVM): the layer is only redrawn when the
 * cell's content changes (`model.active` flips, an image load completes) and is otherwise
 * just translated.
 *
 * On Android the same layer allocates an extra GPU surface per visible cell without a
 * matching win — Android's hardware renderer already caches each cell's `RenderNode`, so the
 * surfaces show up as measurable GPU-memory growth during grid scroll while giving nothing
 * back. It is therefore off on Android.
 */
internal expect val gridCellOffscreenLayerEnabled: Boolean
