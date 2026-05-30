package net.sigmabeta.chipbox.features.search.real

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.chipbox.strings.api.text
import net.sigmabeta.chipbox.common.ui.components.api.subs.MenuActionIcon
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.ui.Icon

private val SidePadding = 16.dp
private const val HINT_ALPHA = 0.5f

@Composable
@Suppress("LongMethod")
fun SearchBar(
    text: String,
    actionSink: ActionSink,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(32.dp)

    // Compose's `shadow` modifier is available cross-platform; the older SDK_INT < Q gate
    // (pre-Renderscript path that fell back to `clip(shape)` on KitKat-Pi) is no longer
    // needed — minSdk is well past Q by now, and the gate blocks the shared appui scaffold
    // from compiling on JVM/desktop. See the M9 nav-unification commit in docs/kmp-migration.md.
    val actualModifier = modifier
        .fillMaxWidth()
        .padding(horizontal = SidePadding)
        .shadow(elevation = 4.dp, shape = shape)

    Surface(modifier = actualModifier) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MenuActionIcon(
                icon = Icon.Back,
                contentDescription = ChipboxStringId.ACCY_CDESC_TOPBAR_BACK,
                onClick = { actionSink.sendAction(SearchAction.BackClicked) },
            )

            val textEmpty = text.isEmpty()

            Box(
                modifier = Modifier
                    .weight(1.0f)
                    .padding(vertical = 4.dp),
            ) {
                val focusRequester = remember { FocusRequester() }
                // Skip the auto-focus under Paparazzi / @Preview: the IME show that follows
                // routes through Layoutlib's HandlerThread mock, which calls
                // Thread.setPosixNicenessInternal(int) and crashes on JDK ≥ 25 (Paparazzi
                // 2.0.0-alpha04 / layoutlib-runtime-16.2.1). LocalInspectionMode is true under
                // both Paparazzi and AS Preview; runtime stays unaffected.
                val skipFocus = LocalInspectionMode.current
                LaunchedEffect(Unit) {
                    if (!skipFocus) focusRequester.requestFocus()
                }

                BasicTextField(
                    value = text,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onPrimaryContainer),
                    onValueChange = { actionSink.sendAction(SearchAction.QueryChanged(it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )

                this@Row.AnimatedVisibility(visible = textEmpty) {
                    Text(
                        text = ChipboxStringId.SEARCH_HINT.text(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier
                            .alpha(HINT_ALPHA)
                            .fillMaxWidth(),
                    )
                }
            }

            AnimatedVisibility(visible = !textEmpty) {
                MenuActionIcon(
                    icon = Icon.Clear,
                    contentDescription = ChipboxStringId.ACCY_CDESC_SEARCH_CLEAR,
                    onClick = { actionSink.sendAction(SearchAction.QueryChanged("")) },
                )
            }
        }
    }
}
