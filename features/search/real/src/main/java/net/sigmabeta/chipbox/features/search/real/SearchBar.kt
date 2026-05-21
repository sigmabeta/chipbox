package net.sigmabeta.chipbox.features.search.real

import android.os.Build
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.strings.ChipboxStringId
import net.sigmabeta.chipbox.strings.text
import net.sigmabeta.chipbox.ui.components.subs.MenuActionIcon
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

    val commonModifier = modifier
        .fillMaxWidth()
        .padding(horizontal = SidePadding)

    val actualModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        commonModifier.shadow(elevation = 4.dp, shape = shape)
    } else {
        commonModifier.clip(shape)
    }

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
                LaunchedEffect(Unit) { focusRequester.requestFocus() }

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
