package net.sigmabeta.chipbox.features.componentlibrary.real

import net.sigmabeta.sage.ui.StringGenerator
import java.util.Random

// generateTitle() (1-5 words) backs both titles and captions: the components' caption slot is a
// short secondary line, so generateLorem()'s up-to-50-word output would overflow it.
internal actual fun generateSampleContent(seed: Long, count: Int): SampleContent {
    val generator = StringGenerator(Random(seed))
    return SampleContent(
        names = List(count) { generator.generateName() },
        titles = List(count) { generator.generateTitle() },
        captions = List(count) { generator.generateTitle() },
    )
}
