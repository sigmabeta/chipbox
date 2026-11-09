package net.sigmabeta.chipbox.features.crashlog.real

// Enforcement-only target (no JS debug UI); a plain epoch string suffices.
internal actual fun formatCrashTimestamp(epochMs: Long): String = epochMs.toString()
