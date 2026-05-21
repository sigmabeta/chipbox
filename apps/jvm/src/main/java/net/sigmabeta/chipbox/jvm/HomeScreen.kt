package net.sigmabeta.chipbox.jvm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * Voyager `Screen` carrying the existing Hello content + a button that pushes
 * [AboutScreen]. The `Screen` interface only requires `@Composable fun Content()`, which is
 * what the parent `Navigator` calls for the topmost screen on the stack.
 *
 * `data object` because the screen has no per-instance state (the VM is pulled via
 * `metroViewModel()` from the JvmChipboxGraph multibinding map). `data` is required by
 * Voyager for screen identity/equality across recompositions.
 */
data object HomeScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val vm: HelloViewModel = metroViewModel()
        val message by vm.message.collectAsState()
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Hello, Chipbox",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = { navigator.push(AboutScreen) }) {
                Text("Go to About")
            }
        }
    }
}
