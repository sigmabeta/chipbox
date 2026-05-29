package net.sigmabeta.chipbox.server.logging

import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.logging.HatchetError

/**
 * Minimal [Hatchet] for the headless server — routes severities to stdout (info/below) and
 * stderr (warn/error). Skips the column-aligned per-frame tag formatting [JvmHatchet] does, which
 * is overkill when the server's logs are scattered between Ktor's request lines (via SLF4J/
 * Logback) and chipbox's scanner output. ERROR+ goes into a 16-deep ring exposed via
 * [recentErrors] for any future debug-info surface to read.
 */
class ServerHatchet : Hatchet {
    override fun v(message: String) = log(VERBOSE, message)
    override fun d(message: String) = log(DEBUG, message)
    override fun i(message: String) = log(INFO, message)
    override fun w(message: String) = log(WARN, message)
    override fun e(message: String) = log(ERROR, message)

    private val errorQueueLock = Any()
    private val errorQueue = ArrayDeque<HatchetError>()

    override val recentErrors: List<HatchetError>
        get() = synchronized(errorQueueLock) { errorQueue.toList() }

    override fun log(severity: Int, message: String) {
        val sink = if (severity >= WARN) System.err else System.out
        val tag = when {
            severity >= ERROR -> "E"
            severity >= WARN -> "W"
            severity >= INFO -> "I"
            severity >= DEBUG -> "D"
            else -> "V"
        }
        sink.println("$tag/chipbox-server: $message")
        if (severity >= ERROR) {
            synchronized(errorQueueLock) {
                errorQueue.addLast(
                    HatchetError(
                        timestamp = System.currentTimeMillis(),
                        tag = "chipbox-server",
                        thread = Thread.currentThread().name,
                        message = message,
                    ),
                )
                if (errorQueue.size > MAX_RECENT_ERRORS) errorQueue.removeFirst()
            }
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
