package net.sigmabeta.chipbox.ui.components.subs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.strings.ChipboxStringId
import net.sigmabeta.chipbox.strings.text
import net.sigmabeta.chipbox.ui.components.R
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.vector

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
                minHeight = dimensionResource(id = R.dimen.min_clickable_size),
                minWidth = dimensionResource(id = R.dimen.min_clickable_size),
            )
            .padding(12.dp)
    )
}
