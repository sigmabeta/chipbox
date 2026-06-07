package net.sigmabeta.chipbox.features.crashlog.real

/**
 * Wall-clock date + time (e.g. "2026-06-07 14:23:01") for a persisted crash timestamp. Crashes
 * outlive the session that produced them, so — unlike the error log's time-of-day — the crash log
 * shows the date too. JVM/Android use java.time; the enforcement-only JS target (no debug UI)
 * returns the raw epoch string.
 */
internal expect fun formatCrashTimestamp(epochMs: Long): String
