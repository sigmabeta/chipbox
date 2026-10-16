package net.sigmabeta.chipbox.abrender

/** Outcome of a [runRender] pass — what the caller (CLI summary or the on-device test) reports. */
data class RenderSummary(val rendered: Int, val skipped: Int, val failed: Int)
