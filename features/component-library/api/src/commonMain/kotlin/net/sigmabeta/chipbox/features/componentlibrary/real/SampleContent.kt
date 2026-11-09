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
