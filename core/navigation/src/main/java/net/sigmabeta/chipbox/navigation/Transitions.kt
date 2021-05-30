package net.sigmabeta.chipbox.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel

// TODO Hacky AF. Once there's a more better API for this, use that.
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun EntryAnimation(
    content: @Composable (ViewModel) -> Unit,
    viewModel: ViewModel
) {
    val visibleState = remember {
        MutableTransitionState(
            initialState = false
        ).apply {
            targetState = true
        }
    }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = slideInHorizontally(initialOffsetX = animationOffset()) + fadeIn(),
        exit = ExitTransition.None
    )
    {
        content(viewModel)
    }
}

@Composable
private fun animationOffset(): (fullWidth: Int) -> Int = {
    it / 4
}