package net.sigmabeta.chipbox.ui.components.subs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp


@Composable
@Suppress("LongParameterList")
fun LabeledThingy(
    label: String,
    thingy: @Composable RowScope.() -> Unit,
    onClick: () -> Unit,
    onClickLabel: String?,
    accyStateDescription: String? = null,
    modifier: Modifier,
    padding: PaddingValues,
    labelColor: Color? = null,
    labelFontWeight: FontWeight? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick,
                onClickLabel = onClickLabel,
            )
            .padding(padding)
            .semantics {
                accyStateDescription?.let { stateDescription = it }
            },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = labelColor ?: MaterialTheme.colorScheme.onBackground,
            fontWeight = labelFontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(vertical = 16.dp)
                .weight(1.0f)
        )

        Spacer(modifier = Modifier.width(8.dp))

        thingy()
    }
}
