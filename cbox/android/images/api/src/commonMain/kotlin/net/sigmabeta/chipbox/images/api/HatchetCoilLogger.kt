package net.sigmabeta.chipbox.images.api

import coil3.util.Logger
import dev.zacsweers.metro.Inject
import net.sigmabeta.sage.analytics.Analytics
import net.sigmabeta.sage.logging.Hatchet

class HatchetCoilLogger @Inject constructor(
    private val hatchet: Hatchet,
    private val analytics: Analytics,
) : Logger {
    override var minLevel = Logger.Level.Verbose

    override fun log(tag: String, level: Logger.Level, message: String?, throwable: Throwable?) {
        if (throwable != null || message?.contains("Exception") == true) {
            val errorString = message ?: throwable?.message ?: "Unknown error."
            hatchet.log(SEVERITY_ERROR, errorString)
            analytics.logError(
                failedOperationName = "ImageLoading",
                errorString = errorString,
                error = throwable ?: RuntimeException(message?.substringAfter("java.")),
            )
        } else {
            hatchet.log(level.ordinal + LOG_LEVEL_OFFSET, message ?: "Somehow a blank message")
        }
    }

    private companion object {
        // Coil's Logger.Level is 0-indexed (Verbose=0, Debug=1, ...). Hatchet/Android log
        // severities start at 2 (VERBOSE). +2 bridges the two without depending on
        // android.util.Log — keeps this class on commonMain so JS can use it too.
        const val LOG_LEVEL_OFFSET = 2
        const val SEVERITY_ERROR = 6
    }
}
