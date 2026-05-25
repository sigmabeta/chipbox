package net.sigmabeta.chipbox.features.settings

/**
 * Localized LONG-style date for the build timestamp shown on the settings screen. JVM/Android use
 * java.time (unchanged); the enforcement-only JS target (no settings screen) returns a plain
 * epoch string.
 */
internal expect fun formatLongDate(epochMs: Long): String
