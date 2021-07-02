package net.sigmabeta.chipbox.navigation

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.navigation.compose.NamedNavArgument

open class NavDestination(
    val route: String,
    val arguments: List<NamedNavArgument>,
    val content: (@Composable (ViewModel, Bundle?) -> Unit),
    val viewModelGenerator: @Composable () -> ViewModel
)

@Composable
fun ComposableOutput(navDestination: NavDestination, arguments: Bundle?) {
    EntryAnimation(navDestination.content, arguments, navDestination.viewModelGenerator())
}