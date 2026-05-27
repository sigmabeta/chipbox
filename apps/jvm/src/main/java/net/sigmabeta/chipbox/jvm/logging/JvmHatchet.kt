package net.sigmabeta.chipbox.jvm.logging

import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.logging.HatchetError

/**
 * JVM-desktop counterpart of `AndroidHatchet`. Mirrors its behaviour as closely as the JVM
 * runtime allows so log sites read identically across targets:
 *
 *  - Severity ints match `android.util.Log` constants (2..7), so the `log(severity, …)`
 *    overload and any call site that passes a raw int stays portable.
 *  - The tag is derived from the first non-Hatchet stack frame, with anonymous-class suffixes
 *    (`Foo$1`) stripped and the result pad/center-ellipsized to a fixed 16-char width so logs
 *    align column-wise. Same convention applied in `AndroidHatchet`.
 *  - The body is `<thread> || <message>` with the thread name pad/center-ellipsized to
 *    16 chars, line-split so multi-line messages keep the same `LEVEL/Tag:` prefix on each
 *    emitted line (logcat does this implicitly via separate `Log.println` calls; here it has
 *    to be explicit).
 *  - Errors (severity ≥ ERROR) are also recorded into a 16-deep ring buffer exposed via
 *    [recentErrors] so a debug UI / crash reporter can surface them. Stored with raw (unpadded)
 *    tag + thread to keep the captured data dense.
 *  - Logging is debug-gated like Android's `BuildConfig.DEBUG` short-circuit. The flag is
 *    constructor-supplied because there's no per-target `BuildConfig` in commonMain and the
 *    JVM app may want to flip it independently of `AppInfo.isDebug`.
 *
 * Differences from `AndroidHatchet`:
 *  - No 4000-char per-call chunking: logcat enforces that limit, `println` does not.
 *  - `Log.wtf` has no JVM analog, so ASSERT routes to `System.err` with an `A/` prefix.
 *  - WARN/ERROR/ASSERT go to `System.err`, the rest to `System.out` — standard desktop
 *    convention, makes piping warning streams trivial.
 */
class JvmHatchet(private val debug: Boolean = true) : Hatchet {
    override fun v(message: String) = logInternal(VERBOSE, message)

    override fun d(message: String) = logInternal(DEBUG, message)

    override fun i(message: String) = logInternal(INFO, message)

    override fun w(message: String) = logInternal(WARN, message)

    override fun e(message: String) = logInternal(ERROR, message)

    override fun log(severity: Int, message: String) = logInternal(severity, message)

    private val errorQueueLock = Any()
    private val errorQueue = ArrayDeque<HatchetError>()

    override val recentErrors: List<HatchetError>
        get() = synchronized(errorQueueLock) { errorQueue.toList() }

    @Suppress("ThrowingExceptionsWithoutMessageOrCause")
    private fun logInternal(severity: Int, message: String) {
        if (!debug) return

        val element = Throwable().stackTrace.firstOrNull { it.className !in fqcnIgnore }
        val tagPair = element?.let(::resolveTag)
        val tag = tagPair?.formatted ?: FALLBACK_TAG
        val threadName = currentFormattedThreadName()
        val body = "$threadName || $message"
        val levelChar = severity.toLevelChar()
        val sink = if (severity >= WARN) System.err else System.out

        // Per-line so the LEVEL/Tag: prefix appears on every wrapped line, matching how the
        // Android side ends up looking after logcat splits a multi-line Log.println call.
        for (line in body.lineSequence()) {
            sink.println("$levelChar/$tag: $line")
        }

        if (severity >= ERROR) {
            recordError(message, tagPair?.raw ?: FALLBACK_TAG_RAW)
        }
    }

    private fun recordError(message: String, rawTag: String) {
        val entry = HatchetError(
            timestamp = System.currentTimeMillis(),
            tag = rawTag,
            thread = Thread.currentThread().name,
            message = message,
        )
        synchronized(errorQueueLock) {
            errorQueue.addLast(entry)
            if (errorQueue.size > MAX_RECENT_ERRORS) errorQueue.removeFirst()
        }
    }

    // Per-thread cache of the padded/ellipsized name so we only rebuild when the thread is
    // renamed (e.g. coroutine dispatchers reusing pool threads with different names).
    private val cachedThreadName = ThreadLocal<Pair<String, String>>()

    private fun currentFormattedThreadName(): String {
        val current = Thread.currentThread().name
        val cached = cachedThreadName.get()
        if (cached != null && cached.first == current) return cached.second
        val formatted = formatThreadName(current)
        cachedThreadName.set(current to formatted)
        return formatted
    }

    private fun formatThreadName(name: String): String = formatFixedWidth(name, THREAD_NAME_WIDTH)

    private fun formatFixedWidth(text: String, width: Int): String = when {
        text.length == width -> text

        text.length < width -> text.padEnd(width)

        else -> {
            val keep = width - ELLIPSIS.length
            val head = (keep + 1) / 2
            val tail = keep / 2
            text.substring(0, head) + ELLIPSIS + text.substring(text.length - tail)
        }
    }

    private val fqcnIgnore = listOf(
        Hatchet::class.java.name,
        JvmHatchet::class.java.name,
    )

    // Per-className cache of (raw, formatted) tags. `raw` is the bare class name with any
    // anonymous-class suffix stripped — fed into [recordError] so the queue stays dense.
    // `formatted` is `raw` pad/center-ellipsized to a fixed 16-char width for output alignment.
    private val cachedTag = ConcurrentHashMap<String, TagPair>()

    private data class TagPair(val raw: String, val formatted: String)

    private fun resolveTag(element: StackTraceElement): TagPair =
        cachedTag.getOrPut(element.className) {
            var raw = element.className.substringAfterLast('.')
            val m = ANONYMOUS_CLASS.matcher(raw)
            if (m.find()) {
                raw = m.replaceAll("")
            }
            TagPair(raw = raw, formatted = formatFixedWidth(raw, TAG_WIDTH))
        }

    @Suppress("MagicNumber")
    private fun Int.toLevelChar() = when (this) {
        VERBOSE -> 'V'
        DEBUG -> 'D'
        INFO -> 'I'
        WARN -> 'W'
        ERROR -> 'E'
        else -> 'A'
    }

    companion object {
        // Mirrors android.util.Log severity ints (VERBOSE..ASSERT).
        private const val VERBOSE = 2
        private const val DEBUG = 3
        private const val INFO = 4
        private const val WARN = 5
        private const val ERROR = 6
        private const val TAG_WIDTH = 16
        private const val THREAD_NAME_WIDTH = 16
        private const val MAX_RECENT_ERRORS = 16
        private const val ELLIPSIS = "..."
        private const val FALLBACK_TAG_RAW = "Chipbox"
        private val FALLBACK_TAG = FALLBACK_TAG_RAW.padEnd(TAG_WIDTH)
        private val ANONYMOUS_CLASS = Pattern.compile("(\\$\\d+)+$")
    }
}
