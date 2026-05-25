package net.sigmabeta.chipbox.strings.real

import net.sigmabeta.chipbox.common.strings.real.generated.resources.Res
import net.sigmabeta.chipbox.common.strings.real.generated.resources.allStringResources
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.ui.SageStringId
import net.sigmabeta.sage.ui.StringProvider
import org.jetbrains.compose.resources.getString

/**
 * The single, multiplatform [StringProvider]. String values live in one place —
 * `src/commonMain/composeResources/values/` — and Compose Multiplatform generates the
 * accessors. [loadChipboxStrings] preloads every [ChipboxStringId]'s text once at startup (Compose's
 * `getString` is `suspend`) into a map, so the synchronous `StringProvider` calls used in
 * non-composable view models resolve without suspension. Replaces the per-platform copies that used
 * to exist (Android `R.string`, the gen_jvm_strings.py output, and the hand-written CLI subset).
 */
class ChipboxStringProvider(private val strings: Map<SageStringId, String>) : StringProvider {
    override fun getString(string: SageStringId): String =
        strings[string] ?: error("No string mapping for $string")

    override fun getStringOneArg(string: SageStringId, arg: String): String =
        formatChipboxString(getString(string), listOf(arg))

    override fun getStringOneInt(string: SageStringId, arg: Int): String =
        formatChipboxString(getString(string), listOf(arg.toString()))

    override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String =
        formatChipboxString(getString(string), listOf(first, second))
}

/**
 * Preload every [ChipboxStringId]'s text from the Compose resources. Call once at startup (it's
 * `suspend`) and hand the result to [ChipboxStringProvider]. Each id maps to the resource whose
 * name is the lowercased enum name (matching the `<string name=...>` keys in the XML).
 */
suspend fun loadChipboxStrings(): Map<SageStringId, String> {
    val strings = mutableMapOf<SageStringId, String>()
    for (id in ChipboxStringId.entries) {
        strings[id] = getString(Res.allStringResources.getValue(id.name.lowercase()))
    }
    return strings
}

/**
 * Multiplatform stand-in for `String.format` over Android-style specifiers (the only ones the
 * strings use): positional `%1$s`/`%2$s`/`%1$d`, then non-positional `%s`/`%d` filled in order.
 */
internal fun formatChipboxString(template: String, args: List<String>): String {
    var result = template
    args.forEachIndexed { index, arg ->
        result = result.replace("%${index + 1}\$s", arg).replace("%${index + 1}\$d", arg)
    }
    var argIndex = 0
    val out = StringBuilder(result.length)
    var i = 0
    while (i < result.length) {
        val isSpecifier = result[i] == '%' &&
            i + 1 < result.length &&
            (result[i + 1] == 's' || result[i + 1] == 'd') &&
            argIndex < args.size
        if (isSpecifier) {
            out.append(args[argIndex])
            argIndex++
            i += 2
        } else {
            out.append(result[i])
            i++
        }
    }
    return out.toString()
}
