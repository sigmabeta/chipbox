plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.compose.kmp)
}

// Multiplatform ViewModel scoping for Compose Multiplatform Chipbox UI. Ships:
//   - a marker `ChipboxViewModel` base class (deliberately *not* `androidx.lifecycle.ViewModel`
//     yet — the KMP lifecycle artifact's onCleared/scope shape can land when the first real
//     Android screen ports and needs it),
//   - a `ViewModelProvider` interface that Android/Desktop each implement against their own
//     DI container (Hilt vs the plain-Dagger JvmChipboxComponent),
//   - a `LocalViewModelProvider` CompositionLocal so feature composables don't need to know
//     which platform's provider is in play, and
//   - a `@Composable inline fun <reified T : ChipboxViewModel> chipboxViewModel(): T` helper.
//
// Initially only a JVM provider exists (in apps/jvm). The Android-side hookup (a Hilt-backed
// provider, or a sibling `expect`/`actual` directly wrapping `hiltViewModel<T>()`) lands when
// the first real Android Compose screen ports to this module — there's no consumer today.
kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.ui.vm.api"
    }
}
