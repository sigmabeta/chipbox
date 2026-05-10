package net.sigmabeta.chipbox.ui.components.subs

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList

@Composable
fun Dropdown(
    defaultExpansion: Boolean = false,
    selectedPosition: Int,
    settingsLabels: ImmutableList<String>,
    onNewOptionSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(defaultExpansion) }

    Box(
        modifier = Modifier
            .animateContentSize()
            .padding(vertical = 16.dp)
            .heightIn(max = 128.dp)
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.tertiary,
                shape = DropdownShape
            )
    ) {
        if (expanded) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = { expanded = false },
            ) {
                settingsLabels.forEachIndexed { index, label ->
                    DropdownMenuItem(
                        onClick = {
                            expanded = false
                            onNewOptionSelected(index)
                        },
                        text = {
                            DropdownItem(
                                value = label,
                                selected = index == selectedPosition,
                            )
                        }
                    )
                }
            }
        }

        DropdownItem(
            value = settingsLabels[selectedPosition],
            selected = true,
            modifier = Modifier.clickable { expanded = true }
        )
    }
}

private val DropdownShape = RoundedCornerShape(8.dp)

@Composable
private fun DropdownItem(
    value: String,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (selected) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onBackground
    }

    Text(
        text = value,
        textAlign = TextAlign.End,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .animateContentSize()
            .padding(8.dp)
    )
}
