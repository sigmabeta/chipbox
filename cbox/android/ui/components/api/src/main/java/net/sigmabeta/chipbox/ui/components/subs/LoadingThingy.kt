package net.sigmabeta.chipbox.ui.components.subs

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


private val SIDE_MARGIN = 16.dp

@Composable
fun LoadingThingy(
    thingy: @Composable RowScope.() -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SIDE_MARGIN),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val simulatedTextHeight = with(LocalDensity.current) {
            20.sp.toDp()
        }
        ElevatedPill(
            modifier = Modifier
                .padding(vertical = 16.dp)
                .height(simulatedTextHeight)
                .weight(1.0f),
            content = { Flasher() }
        )

        Spacer(modifier = Modifier.width(8.dp))

        thingy()
    }
}
