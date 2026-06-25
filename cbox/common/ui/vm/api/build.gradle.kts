plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
    alias(libs.plugins.sage.compose.kmp)
}

// Multiplatform ViewModel base for Compose Multiplatform Chipbox UI. Ships a single
// `ChipboxViewModel` base class that extends `androidx.lifecycle.ViewModel` from the KMP
// lifecycle artifact (`androidx.lifecycle:lifecycle-viewmodel`), which publishes commonMain
// `ViewModel` + `viewModelScope` for both the Android target (Activity/Voyager-backed stores)
// and the JVM target (where Voyager — or the caller — supplies the `ViewModelStore` scope).
// Instances are resolved through Metro + metro-viewmodel (`metroViewModel<T>()`), so no
// provider interface or CompositionLocal indirection lives here.
kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.common.ui.vm.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(libs.androidx.lifecycle.viewmodel)
            }
        }
    }
}
