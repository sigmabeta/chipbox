package net.sigmabeta.chipbox.features.componentlibrary.real

/**
 * Pre-generated random text used to populate the component gallery. Held in state so the strings
 * stay stable across recompositions / config changes (regenerating per [toListItems] call would
 * reshuffle every label on each frame).
 */
data class SampleContent(
    val names: List<String>,
    val titles: List<String>,
    val captions: List<String>,
) {
    fun name(index: Int) = names[index % names.size]
    fun title(index: Int) = titles[index % titles.size]
    fun caption(index: Int) = captions[index % captions.size]
}

/**
 * Builds [count] of each kind of randomized sample string, seeded so a given [seed] always yields
 * the same content. JVM/Android delegate to SAGE's `StringGenerator`; the enforcement-only JS
 * target (no debug UI) returns deterministic placeholders.
 */
internal expect fun generateSampleContent(seed: Long, count: Int): SampleContent
