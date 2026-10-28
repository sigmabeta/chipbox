package net.sigmabeta.chipbox.strings.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import net.sigmabeta.sage.ui.StringProvider

/**
 * Composable access to the chipbox [StringProvider]. Provided once near the root of each
 * platform's Compose tree — `MainActivity` on Android and `DesktopMain` on the JVM target both
 * inject the Metro-graph `StringProvider` (a `ChipboxStringProvider` backed by the single
 * multiplatform string source). Composables resolve a string via [text] / [text1] / [text2] /
 * [textInt] instead of the Android-only `stringResource` factory.
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

/**
 * The content description for a play/pause control, shared by the mini-player and Now Playing.
 * It names the control's action, not its icon: in the error state the icon is a warning but the
 * tap plays (recovers), and while buffering a spinner replaces the icon but the tap pauses.
 */
@Composable
@ReadOnlyComposable
fun playPauseContentDescription(isPlaying: Boolean): String =
    (if (isPlaying) ChipboxStringId.ACCY_CDESC_PAUSE else ChipboxStringId.ACCY_CDESC_PLAY).text()
