package net.sigmabeta.chipbox.features.errorlog.real

/**
 * Wall-clock time-of-day (e.g. "12:34:56") for a recent-error timestamp shown on the debug error
 * log. JVM/Android use java.time; the enforcement-only JS target (no debug UI) returns the raw
 * epoch string.
 */
internal expect fun formatErrorTimestamp(epochMs: Long): String
