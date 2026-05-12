package net.sigmabeta.chipbox.appui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.playerstatus.PlayerStatusAnimDurationMs
import net.sigmabeta.chipbox.playerstatus.PlayerStatusReservedHeight
import net.sigmabeta.sage.android.ui.list.LocalListBottomInset
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import net.sigmabeta.chipbox.features.settings.Settings
import net.sigmabeta.chipbox.playerstatus.PlayerStatus
import net.sigmabeta.chipbox.ui.components.CrossfadeText
import net.sigmabeta.chipbox.ui.list.LocalTitleBarController
import net.sigmabeta.chipbox.ui.list.TitleBarController
import net.sigmabeta.chipbox.ui.theme.AppTheme
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.ui.StringProvider

private val NAV_RAIL_MIN_WIDTH = 480.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChipboxAppUi(stringProvider: StringProvider, modifier: Modifier = Modifier) {
    AppTheme {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()

        val current = TopLevelDestination.entries.firstOrNull { dest ->
            backStackEntry?.destination?.hierarchy?.any { it.hasRoute(dest.routeClass) } == true
        } ?: TopLevelDestination.LIBRARY

        val atTopLevel = TopLevelDestination.entries.any { dest ->
            backStackEntry?.destination?.hierarchy?.any { it.hasRoute(dest.routeClass) } == true
        }

        val titleBarController = remember { TitleBarController() }

        LaunchedEffect(backStackEntry?.destination?.route, atTopLevel) {
            titleBarController.set(
                TitleBarModel(
                    title = if (atTopLevel) stringProvider.getString(current.labelId) else null,
                    shouldShowBack = !atTopLevel,
                )
            )
        }

        val topAppBarState = rememberTopAppBarState()
        val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(topAppBarState)

        LaunchedEffect(backStackEntry?.destination?.route) {
            topAppBarState.contentOffset = 0f
            val startOffset = topAppBarState.heightOffset
            if (startOffset != 0f) {
                animate(
                    initialValue = startOffset,
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 300),
                ) { value, _ ->
                    topAppBarState.heightOffset = value
                }
            }
        }

        val widthDp = LocalConfiguration.current.screenWidthDp.dp
        val layoutType = if (widthDp >= NAV_RAIL_MIN_WIDTH) {
            NavigationSuiteType.NavigationRail
        } else {
            NavigationSuiteType.NavigationBar
        }

        val snackbarHostState = remember { SnackbarHostState() }
        val snackbarScope = rememberCoroutineScope()

        var playerStatusVisible by remember { mutableStateOf(false) }
        val navHostBottomInset by animateDpAsState(
            targetValue = if (playerStatusVisible) PlayerStatusReservedHeight else 0.dp,
            animationSpec = tween(PlayerStatusAnimDurationMs),
            label = "ChipboxAppUi.navHostBottomInset",
        )

        CompositionLocalProvider(LocalTitleBarController provides titleBarController) {
            NavigationSuiteScaffold(
                navigationSuiteItems = navItems(stringProvider, current, navController),
                layoutType = layoutType,
                modifier = modifier,
            ) {
                Scaffold(
                    topBar = {
                        val titleBar = titleBarController.state
                        TopAppBar(
                            title = {
                                CrossfadeText(
                                    text = titleBar.title.orEmpty(),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    textModifier = Modifier.basicMarquee(),
                                )
                            },
                            navigationIcon = {
                                if (titleBar.shouldShowBack) {
                                    IconButton(onClick = { navController.popBackStack() }) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = null,
                                        )
                                    }
                                } else {
                                    IconButton(onClick = { navController.navigate(Settings) }) {
                                        Icon(
                                            imageVector = Icons.Filled.Menu,
                                            contentDescription = null,
                                        )
                                    }
                                }
                            },
                            scrollBehavior = scrollBehavior,
                        )
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                ) { padding ->
                    CompositionLocalProvider(
                        LocalListBottomInset provides navHostBottomInset,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                        ) {
                            ChipboxNavHost(
                                navController = navController,
                                snackbarHostState = snackbarHostState,
                                snackbarScope = snackbarScope,
                                modifier = Modifier.fillMaxSize(),
                            )
                            PlayerStatus(
                                modifier = Modifier.align(Alignment.BottomCenter),
                                onVisibleChange = { playerStatusVisible = it },
                            )
                        }
                    }
                }
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
