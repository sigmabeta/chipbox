package net.sigmabeta.chipbox.core.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.components.R

@OptIn(ExperimentalAnimationApi::class)
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
        elevation = elevation,
        shape = CircleShape,
        onClick = onClick,
        modifier = modifier
            .wrapContentSize()
            .background(Color.Transparent)
            .padding(4.dp)
    ) {
        Icon(
            painter = painterResource(id = iconResource),
            contentDescription = null
        )

        AnimatedVisibility(selected) {
            Text(
                text = stringResource(labelResource),
                maxLines = 1,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun borderStroke(selected: Boolean) =
    BorderStroke(
        animateDpAsState(targetValue = if (selected) 2.dp else 0.dp).value,
        animateColorAsState(
            targetValue = if (selected) {
                MaterialTheme.colors.onPrimary
            } else {
                MaterialTheme.colors.primary
            }
        ).value
    )

private val elevation = object : ButtonElevation {
    @Composable
    override fun elevation(
        enabled: Boolean,
        interactionSource: InteractionSource
    ): State<Dp> {
        return object : State<Dp> {
            override val value: Dp
                get() = 0.dp
        }
    }
}

@Preview
@Composable
fun PreviewNavButtonSelected() {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colors.primary)
            .fillMaxWidth()
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
            .fillMaxWidth()
    ) {
        NavButton(
            selected = false,
            labelResource = R.string.label_nav_artists,
            iconResource = R.drawable.ic_artist_24,
            onClick = { }
        )
    }
}

