package net.sigmabeta.chipbox.activities

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.insets.LocalWindowInsets
import net.sigmabeta.chipbox.core.components.ChipboxNavBar
import net.sigmabeta.chipbox.core.components.MenuItemDefinition
import net.sigmabeta.chipbox.navigation.ComposableOutput
import net.sigmabeta.chipbox.navigation.destinations

@Composable
fun TopScreen() {
    Column(
        Modifier
            .fillMaxHeight()
            .fillMaxWidth()
    ) {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()

        NavHost(
            navController,
            startDestination = "games",
            builder = navGraph(navController),
            modifier = Modifier
                .weight(1.0f)
                .fillMaxWidth()
        )

        val insets = LocalWindowInsets.current
        val context = LocalContext.current

        val menuVisible = remember { mutableStateOf(false) }

        ChipboxNavBar(
            navBackStackEntry?.destination?.route ?: "",
            contentPadding = with(LocalDensity.current) {
                PaddingValues(
                    insets.navigationBars.left.toDp(),
                    0.dp,
                    insets.navigationBars.right.toDp() + 4.dp,
                    insets.navigationBars.bottom.toDp(),
                )
            },
            menuVisible = menuVisible.value,
            listOf(
                MenuItemDefinition(
                    R.drawable.ic_refresh_24,
                    R.string.menu_label_scan_for_music
                ) {
                    Toast.makeText(
                        context,
                        "Scan Library",
                        Toast.LENGTH_SHORT
                    ).show()
                    menuVisible.value = false
                }
            ),
            onNavClick = { destination ->
                try {
                    navController.navigate(destination) {
                        launchSingleTop = true
                        popUpTo(
                            navController.graph.startDestinationRoute ?: "games"
                        ) { }
                    }
                } catch (ex: IllegalArgumentException) {
                    Toast.makeText(
                        context,
                        "Couldn't generate a screen for route \"$destination\"",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onMenuClick = {
                menuVisible.value = !menuVisible.value
            }
        )
    }
}

@Composable
private fun navGraph(navController: NavController): NavGraphBuilder.() -> Unit = {
    val destinationList = destinations { navController.navigate(it) }

    destinationList.forEach { destination ->
        composable(destination.route, destination.arguments) {
            ComposableOutput(destination, it.arguments)
        }
    }
}

