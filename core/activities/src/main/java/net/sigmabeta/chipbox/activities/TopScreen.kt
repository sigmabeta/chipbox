package net.sigmabeta.chipbox.activities

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.insets.LocalWindowInsets
import net.sigmabeta.chipbox.core.components.ChipboxNavBar
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

        ChipboxNavBar(navBackStackEntry?.destination?.route ?: "", insets) { destination ->
            try {
                navController.navigate(destination) {
                    launchSingleTop = true
                    popUpTo(navController.graph.startDestinationRoute ?: "games") { }
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

