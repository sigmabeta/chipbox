package net.sigmabeta.chipbox.features.settings

// Enforcement-only target (no JS settings screen); a plain epoch string suffices.
internal actual fun formatLongDate(epochMs: Long): String = epochMs.toString()
