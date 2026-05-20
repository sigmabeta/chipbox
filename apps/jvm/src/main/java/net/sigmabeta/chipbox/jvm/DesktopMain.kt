package net.sigmabeta.chipbox.jvm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import net.sigmabeta.chipbox.jvm.di.JvmChipboxComponent
import net.sigmabeta.chipbox.ui.theme.ChipboxTheme
import net.sigmabeta.chipbox.ui.theme.tokens.ChipboxFontDefaults
import net.sigmabeta.chipbox.ui.vm.LocalViewModelProvider
import net.sigmabeta.chipbox.ui.vm.chipboxViewModel

/**
 * Bootstrap Compose Multiplatform entry point for the JVM/desktop target. [ChipboxTheme]
 * supplies the shared color palette + Chipbox typography + pixel-art fonts; the
 * [LocalViewModelProvider] hands the [JvmViewModelProvider] (backed by the plain-Dagger
 * graph) to descendants so a composable can call `chipboxViewModel<HelloViewModel>()` without
 * knowing whether it's on Android or JVM.
 */
@Composable
private fun HelloChipbox() {
    val brand = ChipboxFontDefaults.Brand
    val plain = ChipboxFontDefaults.Plain
    ChipboxTheme(
        brand = brand.toFontFamily(),
        plain = plain.toFontFamily(),
        brandScale = brand.scaleFactor,
        plainScale = plain.scaleFactor,
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val vm: HelloViewModel = chipboxViewModel()
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
            }
        }
    }
}

fun runDesktop(component: JvmChipboxComponent) = application {
    val provider = JvmViewModelProvider(component)
    Window(onCloseRequest = ::exitApplication, title = "Chipbox") {
        CompositionLocalProvider(LocalViewModelProvider provides provider) {
            HelloChipbox()
        }
    }
}
