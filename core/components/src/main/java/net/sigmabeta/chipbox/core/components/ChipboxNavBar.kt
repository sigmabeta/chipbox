package net.sigmabeta.chipbox.core.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.BottomAppBar
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.accompanist.insets.WindowInsets
import net.sigmabeta.chipbox.components.R

@Composable
fun ChipboxNavBar(
    insets: WindowInsets,
    navController: NavHostController
) {
    var selectedId by remember { mutableStateOf(R.string.label_nav_games) }
    BottomAppBar(
        contentPadding = with(LocalDensity.current) {
            PaddingValues(
                insets.navigationBars.left.toDp(),
                0.dp,
                insets.navigationBars.right.toDp() + 4.dp,
                insets.navigationBars.bottom.toDp(),
            )
        }
    ) {
        IconButton(onClick = { /*TODO*/ }) {
            Icon(
                painter = painterResource(id = R.drawable.ic_menu_24),
                contentDescription = stringResource(R.string.cont_desc_button_menu)
            )
        }

        navButtonList().forEach {
            val selected = it.labelId == selectedId

            val weight = if (selected) {
                Modifier.weight(1.0f)
            } else {
                Modifier.weight(0.5f)
            }

            NavButton(
                selected,
                it.labelId,
                it.iconId,
                weight
            ) {
                selectedId = it.labelId
                navController.navigate(it.destination) {
                    launchSingleTop = true
                    popUpTo("games") { }
                }
            }
        }
    }
}

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
fun PreviewGamesSelected() {
    ChipboxNavBar(
        insets = WindowInsets.Empty,
        navController = NavHostController(LocalContext.current)
    )
}

@Preview
@Composable
fun PreviewArtistsSelected() {
    ChipboxNavBar(
        insets = WindowInsets.Empty,
        navController = NavHostController(LocalContext.current)
    )
}

@Preview
@Composable
fun PreviewPlaylistsSelected() {
    ChipboxNavBar(
        insets = WindowInsets.Empty,
        navController = NavHostController(LocalContext.current)
    )
}