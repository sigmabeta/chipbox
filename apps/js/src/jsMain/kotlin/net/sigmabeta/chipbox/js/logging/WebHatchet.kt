package net.sigmabeta.chipbox.js.logging

import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.logging.HatchetError

/**
 * Browser-side [Hatchet] that routes severities to `console.log`/`warn`/`error`. The fixed-width
 * tag formatting that [net.sigmabeta.chipbox.jvm.logging.JvmHatchet] does for column-aligned
 * stdout is skipped — the dev-tools console isn't a fixed-width tty, so the formatting churn buys
 * nothing. ERROR+ also accumulates into a 16-deep ring for any debug-info surface that reads it.
 */
class WebHatchet : Hatchet {
    override fun v(message: String) = log(VERBOSE, message)
    override fun d(message: String) = log(DEBUG, message)
    override fun i(message: String) = log(INFO, message)
    override fun w(message: String) = log(WARN, message)
    override fun e(message: String) = log(ERROR, message)

    private val errorQueue = ArrayDeque<HatchetError>()

    override val recentErrors: List<HatchetError>
        get() = errorQueue.toList()

    override fun log(severity: Int, message: String) {
        when {
            severity >= ERROR -> console.error(message)
            severity >= WARN -> console.warn(message)
            else -> console.log(message)
        }
        if (severity >= ERROR) {
            errorQueue.addLast(
                HatchetError(
                    timestamp = 0L,
                    tag = "Chipbox",
                    thread = "js",
                    message = message,
                ),
            )
            if (errorQueue.size > MAX_RECENT_ERRORS) errorQueue.removeFirst()
        }
    }

    private companion object {
        const val VERBOSE = 2
        const val DEBUG = 3
        const val INFO = 4
        const val WARN = 5
        const val ERROR = 6
        const val MAX_RECENT_ERRORS = 16
    }
}

private external object console {
    fun log(message: String)
    fun warn(message: String)
    fun error(message: String)
}
