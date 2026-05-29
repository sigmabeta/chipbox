package net.sigmabeta.chipbox.common.ui.components.api.subs

/**
 * JVM + Android: keep the per-cell crossfade animations. Compose's Long-heavy slot table is
 * fine when Long is a real 64-bit hardware type; the visible polish on source / state
 * transitions outweighs the slot overhead.
 */
internal actual val crossfadeImagesEnabled: Boolean = true
