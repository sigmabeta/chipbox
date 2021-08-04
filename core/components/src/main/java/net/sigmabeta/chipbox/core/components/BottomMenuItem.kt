package net.sigmabeta.chipbox.core.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.components.R

@Composable
fun BottomMenuItem(
    @DrawableRes iconId: Int,
    @StringRes labelId: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(72.dp)
            .clickable(onClick = onClick)
    ) {
        Image(
            painter = painterResource(id = iconId), 
            contentDescription = stringResource(id = labelId),
            modifier = Modifier
                .align(CenterVertically)
                .height(48.dp)
                .width(48.dp)
                .padding(10.dp)
        )
        Text(
            text = stringResource(id = labelId),
            color = MaterialTheme.colors.onPrimary,
            style = MaterialTheme.typography.body1,
            modifier = Modifier
                .align(CenterVertically)
                .padding(start = 8.dp, end = 16.dp)
                .weight(1.0f)
        )
    }
}

@Preview
@Composable
fun PreviewBottomMenuItem() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
            MaterialTheme.colors.primary
        )
    ) {
        BottomMenuItem(
            iconId = R.drawable.ic_shuffle_24,
            labelId = R.string.caption_unknown_artist,
            {}
        )
    }
}