package net.sigmabeta.chipbox.core.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.BottomAppBar
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.insets.LocalWindowInsets
import net.sigmabeta.chipbox.components.R

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ChipboxNavBar(
    selectedDestination: String,
    contentPadding: PaddingValues,
    menuVisible: Boolean,
    menuItems: List<MenuItemDefinition>,
    onNavClick: (destination: String) -> Unit,
    onMenuClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .background(color = MaterialTheme.colors.primary)
            .padding(contentPadding)
    ) {
        BottomAppBar(
            elevation = 0.dp
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_menu_24),
                    contentDescription = stringResource(R.string.cont_desc_button_menu)
                )
            }

            navButtonList().forEach {
                val selected = it.destination == selectedDestination

                val weight = if (selected) {
                    Modifier
                } else {
                    Modifier.weight(1.0f)
                }

                NavButton(
                    selected,
                    it.labelId,
                    it.iconId,
                    weight
                ) {
                    onNavClick(it.destination)
                }
            }
        }

        AnimatedVisibility(visible = menuVisible) {
            menuItems.forEach {
                BottomMenuItem(
                    iconId = it.iconId,
                    labelId = it.labelId,
                    onClick = it.onClick
                )
            }
        }
    }
}

data class MenuItemDefinition(
    @DrawableRes val iconId: Int,
    @StringRes val labelId: Int,
    val onClick: () -> Unit
)

data class NavButtonDefinition(
    val destination: String,
    @StringRes val labelId: Int,
    @DrawableRes val iconId: Int
)

fun navButtonList() = listOf(
    NavButtonDefinition("games", R.string.label_nav_games, R.drawable.ic_album_24),
    NavButtonDefinition("artists", R.string.label_nav_artists, R.drawable.ic_artist_24),
    NavButtonDefinition("playlists", R.string.label_nav_playlists, R.drawable.ic_list_24)
)

@Preview
@Composable
fun PreviewMenuHidden() {
    var selectedDestination by remember { mutableStateOf("games") }
    val insets = LocalWindowInsets.current

    ChipboxNavBar(
        selectedDestination = selectedDestination,
        contentPadding = with(LocalDensity.current) {
            PaddingValues(
                insets.navigationBars.left.toDp(),
                0.dp,
                insets.navigationBars.right.toDp() + 4.dp,
                insets.navigationBars.bottom.toDp(),
            )
        },
        menuVisible = false,
        menuItems = emptyList(),
        onNavClick = {
            selectedDestination = it
        },
        onMenuClick = {

        }
    )
}

@Preview
@Composable
fun PreviewMenuShown() {
    var selectedDestination by remember { mutableStateOf("games") }
    val insets = LocalWindowInsets.current

    ChipboxNavBar(
        selectedDestination = selectedDestination,
        contentPadding = with(LocalDensity.current) {
            PaddingValues(
                insets.navigationBars.left.toDp(),
                0.dp,
                insets.navigationBars.right.toDp() + 4.dp,
                insets.navigationBars.bottom.toDp(),
            )
        },
        menuVisible = true,
        menuItems = listOf(
            MenuItemDefinition(
                iconId = R.drawable.ic_refresh_24,
                labelId = R.string.caption_unknown_artist,
                onClick = {}
            )
        ),
        onNavClick = {
            selectedDestination = it
        },
        onMenuClick = {

        }
    )
}