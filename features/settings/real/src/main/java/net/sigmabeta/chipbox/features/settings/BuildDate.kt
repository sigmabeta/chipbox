package net.sigmabeta.chipbox.features.settings

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val BUILD_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter
    .ofLocalizedDate(FormatStyle.LONG)

internal actual fun formatLongDate(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .format(BUILD_DATE_FORMATTER)
