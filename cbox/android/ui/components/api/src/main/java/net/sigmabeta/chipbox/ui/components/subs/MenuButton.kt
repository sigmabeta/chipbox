package net.sigmabeta.chipbox.ui.components.subs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun MenuButton(
    onClick: () -> Unit,
    label: String,
    iconId: Int,
    modifier: Modifier = Modifier
) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val border = remember(onPrimary) { BorderStroke(width = 1.dp, color = onPrimary) }

    OutlinedButton(
        onClick = onClick,
        border = border,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = onPrimary),
        modifier = modifier
    ) {
        Icon(
            painter = painterResource(id = iconId),
            contentDescription = null
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
