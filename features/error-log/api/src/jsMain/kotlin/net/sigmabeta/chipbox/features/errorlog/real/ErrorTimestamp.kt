package net.sigmabeta.chipbox.features.errorlog.real

// Enforcement-only target (no JS debug UI); a plain epoch string suffices.
internal actual fun formatErrorTimestamp(epochMs: Long): String = epochMs.toString()
