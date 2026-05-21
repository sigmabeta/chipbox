package net.sigmabeta.chipbox.ui.components.subs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.strings.ChipboxStringId
import net.sigmabeta.chipbox.strings.text
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.vector

private val MIN_CLICKABLE_SIZE = 48.dp

@Composable
fun MenuActionIcon(
    icon: Icon,
    contentDescription: ChipboxStringId,
    onClick: () -> Unit
) {
    Icon(
        imageVector = icon.vector(),
        contentDescription = contentDescription.text(),
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .clickable(onClick = onClick)
            .defaultMinSize(
                minHeight = MIN_CLICKABLE_SIZE,
                minWidth = MIN_CLICKABLE_SIZE,
            )
            .padding(12.dp)
    )
}
