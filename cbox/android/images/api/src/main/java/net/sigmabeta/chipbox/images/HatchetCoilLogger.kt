package net.sigmabeta.chipbox.images

import android.util.Log
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
            hatchet.log(Log.ERROR, errorString)
            analytics.logError(
                failedOperationName = "ImageLoading",
                errorString = errorString,
                error = throwable ?: RuntimeException(message?.substringAfter("java.")),
            )
        } else {
            hatchet.log(level.ordinal + 2, message ?: "Somehow a blank message")
        }
    }
}
