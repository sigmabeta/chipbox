package net.sigmabeta.chipbox.common.ui.components.api.subs

/**
 * JS (Compose Web, Skiko): keep the per-cell offscreen layer — the same Skiko win as JVM, and
 * the original motivation for adding it (`perf(apps/js,ui): fix grid-scroll jank on web`). See
 * [gridCellOffscreenLayerEnabled].
 */
internal actual val gridCellOffscreenLayerEnabled: Boolean = true
