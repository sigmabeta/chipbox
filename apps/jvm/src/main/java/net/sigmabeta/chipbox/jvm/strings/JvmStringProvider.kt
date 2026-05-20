package net.sigmabeta.chipbox.jvm.strings

import net.sigmabeta.sage.ui.SageStringId
import net.sigmabeta.sage.ui.StringProvider

/**
 * JVM analog of `sage.android.ui.strings.AndroidStringProvider`. The Android side resolves a
 * `SageStringId` to an `R.string` id and lets `Resources.getString(id, args...)` format it; the
 * JVM side has no resources framework, so the caller supplies the English text up front as a
 * `Map<SageStringId, String>` and the arg variants delegate to `String.format()` (which uses
 * the same `%s`/`%d` syntax Android's resource strings already use).
 *
 * Strings missing from the map throw — a wrong ID is a programming error caught at first use,
 * not a silently rendered empty string.
 */
class JvmStringProvider(
    private val strings: Map<SageStringId, String>,
) : StringProvider {
    override fun getString(string: SageStringId): String = lookup(string)

    override fun getStringOneArg(string: SageStringId, arg: String): String =
        lookup(string).format(arg)

    override fun getStringOneInt(string: SageStringId, arg: Int): String =
        lookup(string).format(arg)

    override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String =
        lookup(string).format(first, second)

    private fun lookup(string: SageStringId): String =
        strings[string] ?: error("No JVM string mapping for $string")
}
