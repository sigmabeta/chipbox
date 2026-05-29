package net.sigmabeta.chipbox.common.ui.components.api.subs

/**
 * JS: turn the per-cell crossfade animations off. The AnimatedContent + Crossfade wrappers
 * add slot-table entries that compound with Kotlin/JS's emulated Long ops — every recomposition
 * pays the cost via thousands of `Long.add` / `Long.equalsLong` calls in Compose internals.
 * See [crossfadeImagesEnabled] for the full reasoning.
 */
internal actual val crossfadeImagesEnabled: Boolean = false
