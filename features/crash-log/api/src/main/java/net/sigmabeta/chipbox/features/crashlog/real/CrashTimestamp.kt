package net.sigmabeta.chipbox.features.crashlog.real

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val CRASH_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

internal actual fun formatCrashTimestamp(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .format(CRASH_TIME_FORMATTER)
