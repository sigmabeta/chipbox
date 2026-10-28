package net.sigmabeta.chipbox.features.settings

import kotlin.js.Date

/**
 * Localized date for the build timestamp on the browser Settings screen. Uses the browser's own
 * locale via `Date.toLocaleDateString`, matching the desktop/Android "long date" intent without
 * pulling a date-time library into the JS bundle.
 */
internal actual fun formatLongDate(epochMs: Long): String =
    Date(epochMs.toDouble()).toLocaleDateString()
