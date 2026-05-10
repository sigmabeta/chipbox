package net.sigmabeta.chipbox.appui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import net.sigmabeta.chipbox.ui.theme.AppTheme
import net.sigmabeta.sage.ui.StringProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChipboxAppUi(stringProvider: StringProvider, modifier: Modifier = Modifier) {
    AppTheme {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()

        val current = TopLevelDestination.entries.firstOrNull { dest ->
            backStackEntry?.destination?.hierarchy?.any { it.hasRoute(dest.routeClass) } == true
        } ?: TopLevelDestination.LIBRARY

        val topAppBarState = rememberTopAppBarState()
        val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(topAppBarState)

        NavigationSuiteScaffold(
            navigationSuiteItems = navItems(stringProvider, current, navController),
            modifier = modifier,
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringProvider.getString(current.labelId)) },
                        scrollBehavior = scrollBehavior,
                    )
                },
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            ) { padding ->
                ChipboxNavHost(
                    navController = navController,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun navItems(
    stringProvider: StringProvider,
    current: TopLevelDestination,
    navController: NavHostController,
): NavigationSuiteScope.() -> Unit = {
    TopLevelDestination.entries.forEach { dest ->
        item(
            selected = dest == current,
            onClick = {
                navController.navigate(dest.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(dest.icon, contentDescription = null) },
            label = { Text(stringProvider.getString(dest.labelId)) },
        )
    }
}
