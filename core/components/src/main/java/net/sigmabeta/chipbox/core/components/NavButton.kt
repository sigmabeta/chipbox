package net.sigmabeta.chipbox.core.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.components.R

@Composable
fun NavButton(
    selected: Boolean,
    @StringRes labelResource: Int,
    @DrawableRes iconResource: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        border = borderStroke(selected),
        shape = CircleShape,
        onClick = onClick,
        modifier = modifier
            .background(Color.Transparent)
            .padding(4.dp)
    ) {
        Icon(
            painter = painterResource(id = iconResource),
            contentDescription = null
        )

        if (selected) {
            Text(
                text = stringResource(labelResource),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun borderStroke(selected: Boolean) = if (selected){
    BorderStroke(2.dp, MaterialTheme.colors.onPrimary)
} else {
    BorderStroke(0.dp, MaterialTheme.colors.primary)
}

@Preview
@Composable
fun PreviewNavButtonSelected() {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colors.primary)
    ) {
        NavButton(
            selected = true,
            labelResource = R.string.label_nav_games,
            iconResource = R.drawable.ic_album_24,
            onClick = { }
        )
    }
}

@Preview
@Composable
fun PreviewNavButtonUnselected() {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colors.primary)
    ) {
        NavButton(
            selected = false,
            labelResource = R.string.label_nav_artists,
            iconResource = R.drawable.ic_artist_24,
            onClick = { }
        )
    }
}