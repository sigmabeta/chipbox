package net.sigmabeta.chipbox.features.componentlibrary.real

// Enforcement-only target (no JS debug UI); deterministic placeholders suffice.
internal actual fun generateSampleContent(seed: Long, count: Int): SampleContent = SampleContent(
    names = List(count) { "Sample Name ${it + 1}" },
    titles = List(count) { "Sample Title ${it + 1}" },
    captions = List(count) { "Sample caption ${it + 1}" },
)
