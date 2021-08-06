package net.sigmabeta.chipbox.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun LabelValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(
                start = 16.dp,
                end = 16.dp
            )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.subtitle2,
            textAlign = TextAlign.Start,
            color = MaterialTheme.colors.onPrimary,
            modifier = Modifier
                .wrapContentSize()
                .align(CenterVertically)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.End,
            color = MaterialTheme.colors.onPrimary,
            modifier = Modifier
                .weight(1.0f)
                .align(CenterVertically)
        )
    }
}

@Preview
@Composable
fun PreviewLabelValueRow() {
    Box(
        modifier = Modifier.background(MaterialTheme.colors.primary)
    ) {
        LabelValueRow("Games Found", "35")
    }
}