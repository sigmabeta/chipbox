package net.sigmabeta.chipbox.features.errorlog.real

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ERROR_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

internal actual fun formatErrorTimestamp(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .format(ERROR_TIME_FORMATTER)
