package net.sigmabeta.chipbox.strings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import net.sigmabeta.sage.ui.StringProvider

/**
 * Composable access to the chipbox [StringProvider]. Provided once near the root of each
 * platform's Compose tree — `MainActivity` on Android (Metro-injected `StringProvider`),
 * `DesktopMain` on the JVM target (graph-injected `JvmStringProvider`). Composables that
 * need a string resolve it via [text] / [text1] / [text2] / [textInt] instead of the
 * Android-only `stringResource` factory.
 */
val LocalChipboxStringProvider = staticCompositionLocalOf<StringProvider> {
    error(
        "LocalChipboxStringProvider not provided. Wrap your Compose tree in " +
            "CompositionLocalProvider(LocalChipboxStringProvider provides yourStringProvider) { ... }",
    )
}

@Composable
@ReadOnlyComposable
fun ChipboxStringId.text(): String = LocalChipboxStringProvider.current.getString(this)

@Composable
@ReadOnlyComposable
fun ChipboxStringId.text(arg: String): String = LocalChipboxStringProvider.current.getStringOneArg(this, arg)

@Composable
@ReadOnlyComposable
fun ChipboxStringId.textInt(arg: Int): String = LocalChipboxStringProvider.current.getStringOneInt(this, arg)

@Composable
@ReadOnlyComposable
fun ChipboxStringId.text(first: String, second: String): String =
    LocalChipboxStringProvider.current.getStringTwoArgs(this, first, second)
