package net.sigmabeta.chipbox.features.componentlibrary.real

/**
 * Builds [count] of each kind of randomized sample string, seeded so a given [seed] always yields
 * the same content. JVM/Android delegate to SAGE's `StringGenerator`; the enforcement-only JS
 * target (no debug UI) returns deterministic placeholders.
 */
internal expect fun generateSampleContent(seed: Long, count: Int): SampleContent
