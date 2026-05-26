package net.sigmabeta.chipbox.jvm.logging

import java.util.regex.Pattern
import net.sigmabeta.sage.logging.Hatchet

/**
 * JVM-desktop counterpart of `AndroidHatchet`. Mirrors its behaviour as closely as the JVM
 * runtime allows so log sites read identically across targets:
 *
 *  - Severity ints match `android.util.Log` constants (2..7), so the `log(severity, …)`
 *    overload and any call site that passes a raw int stays portable.
 *  - The tag is derived from the first non-Hatchet stack frame, with anonymous-class suffixes
 *    (`Foo$1`) stripped and the result capped at 23 chars — same convention Android enforces,
 *    kept here for visual parity when logs from both targets sit side-by-side.
 *  - The body is `Thr: <thread> | Msg: <message>`, line-split so multi-line messages keep the
 *    same `LEVEL/Tag:` prefix on each emitted line (logcat does this implicitly via separate
 *    `Log.println` calls; here it has to be explicit).
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

    private fun logInternal(severity: Int, message: String) {
        if (!debug) return

        val tag = currentTag ?: FALLBACK_TAG
        val threadName = Thread.currentThread().name
        val body = "Thr: $threadName | Msg: $message"
        val levelChar = severity.toLevelChar()
        val sink = if (severity >= WARN) System.err else System.out

        // Per-line so the LEVEL/Tag: prefix appears on every wrapped line, matching how the
        // Android side ends up looking after logcat splits a multi-line Log.println call.
        for (line in body.lineSequence()) {
            sink.println("$levelChar/$tag: $line")
        }
    }

    private val fqcnIgnore = listOf(
        Hatchet::class.java.name,
        JvmHatchet::class.java.name,
    )

    @Suppress("ThrowingExceptionsWithoutMessageOrCause")
    private val currentTag: String?
        get() = Throwable().stackTrace
            .firstOrNull { it.className !in fqcnIgnore }
            ?.let(::createStackElementTag)

    /**
     * Derive a tag from the caller frame's class name: drop the package, strip any anonymous-
     * class suffix (`Foo$1`, `Foo$1$2`), and truncate to Android's 23-char limit. Kept identical
     * to `AndroidHatchet.createStackElementTag` so identical call sites produce identical tags.
     */
    private fun createStackElementTag(element: StackTraceElement): String {
        var tag = element.className.substringAfterLast('.')
        val m = ANONYMOUS_CLASS.matcher(tag)
        if (m.find()) {
            tag = m.replaceAll("")
        }
        return if (tag.length <= MAX_TAG_LENGTH) tag else tag.substring(0, MAX_TAG_LENGTH)
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
        private const val MAX_TAG_LENGTH = 23
        private const val FALLBACK_TAG = "Chipbox"
        private val ANONYMOUS_CLASS = Pattern.compile("(\\$\\d+)+$")
    }
}
