package net.sigmabeta.chipbox.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel

open class NavDestination(
    val name: String,
    private val id: Long?,
    private val navigateAction: (String) -> Unit,
    val content: (@Composable (ViewModel) -> Unit)
) {
    fun getRoute(otherDestinationId: Long?) = when {
        otherDestinationId != null -> "$name/$otherDestinationId"
        this.id != null -> "$name/$id"
        else -> name
    }
}

@Composable
fun ComposableOutput(navDestination: NavDestination, viewModel: ViewModel) {
    EntryAnimation(navDestination.content, viewModel)
}