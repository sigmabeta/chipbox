package net.sigmabeta.chipbox.activities

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
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
import com.google.accompanist.insets.WindowInsets
import net.sigmabeta.chipbox.core.components.ChipboxNavBar
import net.sigmabeta.chipbox.core.components.MenuItemDefinition
import net.sigmabeta.chipbox.models.state.ScannerEvent
import net.sigmabeta.chipbox.models.state.ScannerState
import net.sigmabeta.chipbox.navigation.ComposableOutput
import net.sigmabeta.chipbox.navigation.destinations

@Composable
fun TopScreen(viewModel: TopViewModel) {
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

        val scannerState = viewModel.scannerStates.collectAsState(initial = ScannerState.Unknown).value
        val lastScannerEvent = viewModel.scannerEvents.collectAsState(initial = ScannerEvent.Unknown).value

        ChipboxNavBar(
            navBackStackEntry?.destination?.route ?: "",
            contentPadding = navBarPadding(insets),
            menuVisible = menuVisible.value,
            menuItems = menuItems(viewModel, menuVisible),
            scannerState,
            lastScannerEvent,
            onNavClick = navClickHandler(navController, context),
            onMenuClick = { menuVisible.value = !menuVisible.value }
        )
    }
}

fun navClickHandler(navController: NavController, context: Context): (destination: String) -> Unit {
    return { destination ->
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
    }
}

@Composable
private fun navBarPadding(insets: WindowInsets) : PaddingValues {
    with(LocalDensity.current) {
        return PaddingValues(
            insets.navigationBars.left.toDp(),
            0.dp,
            insets.navigationBars.right.toDp() + 4.dp,
            insets.navigationBars.bottom.toDp(),
        )
    }
}

@Composable
private fun menuItems(
    viewModel: TopViewModel,
    menuVisible: MutableState<Boolean>
) = listOf(
    MenuItemDefinition(
        R.drawable.ic_refresh_24,
        R.string.menu_label_scan_for_music
    ) {
        viewModel.startScan()
        menuVisible.value = false
    }
)

@Composable
private fun navGraph(navController: NavController): NavGraphBuilder.() -> Unit = {
    val destinationList = destinations { navController.navigate(it) }

    destinationList.forEach { destination ->
        composable(destination.route, destination.arguments) {
            ComposableOutput(destination, it.arguments)
        }
    }
}

