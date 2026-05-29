package net.sigmabeta.chipbox.common.ui.components.api.subs

/**
 * Platform gate for the per-cell [androidx.compose.animation.AnimatedContent] +
 * [androidx.compose.animation.Crossfade] wrappers in [CrossfadeImage]. JVM + Android get the
 * smooth transitions (their Compose runtime handles the slot-table churn fine); JS turns them
 * off because Kotlin/JS's `Long` emulation makes the per-cell animation infrastructure
 * disproportionately expensive — Compose's slot table is Long-heavy, every Long op is a JS
 * function call instead of a hardware op, and 50 visible grid cells × per-frame recomposition
 * × extra slot entries per cell adds up to visible scroll jank.
 *
 * Functionally the wrappers buy two things: fade-on-source-change (essentially never happens
 * during scroll — cells are re-instantiated per index, not re-keyed in place) and
 * fade-on-state-change (loading → success transitions are over in ~300ms and acceptable to
 * cut on JS).
 */
internal expect val crossfadeImagesEnabled: Boolean
