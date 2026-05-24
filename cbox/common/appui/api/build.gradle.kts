import net.sigmabeta.sage.plugins.components.chipboxNamespace

plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.compose.kmp)
    alias(libs.plugins.metro)
}

// Promoted from sage.android → sage.kmp so the same `ChipboxAppUi()` composable drives the
// Android `MainActivity` and the JVM `DesktopMain.kt`. Voyager + JetBrains-Compose `material3-
// adaptive-navigation-suite` are both cross-platform; the Android-only pieces of the old
// `buildOuterSink` (Intent.ACTION_VIEW, ClipboardManager) became platform-callback parameters
// in [ChipboxAppUi], filled in by each app entry point.
kotlin {
    androidLibrary {
        namespace = chipboxNamespace()
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                // PlayerStatusViewModel + the feature `:real` modules below need `api()` (not
                // `implementation()`) so their @ContributesIntoMap VMs are aggregated into the
                // host app's Metro graph (ChipboxAppGraph / JvmChipboxGraph) when the app
                // module compiles. Gradle's `implementation` hides transitive types from
                // second-level consumers — Metro's FIR pass then doesn't see those hints in
                // the app module. Promoting to `api` re-exposes them. Discovered when
                // LibraryViewModel started crashing at runtime with `Unknown model class`
                // (M9 slice 5d of docs/kmp-migration.md).
                api(projects.cbox.common.playerStatus.api)
                implementation(projects.cbox.common.ui.chrome.api)
                implementation(projects.cbox.common.ui.components.api)
                implementation(projects.cbox.common.ui.list.api)
                implementation(projects.cbox.common.appcomm.api)
                implementation(projects.cbox.common.strings.api)
                // Shared `ChipboxTheme()` — multiplatform. The Android-side `AppTheme()`
                // wrapper in `cbox/android/ui/theme/api` was thin syntactic sugar and is
                // no longer referenced from this module.
                implementation(projects.cbox.common.ui.theme.api)
                // ChipboxSettingsManager + ThemeMode — read by ChipboxAppUiViewModel to pick
                // the light/dark color scheme passed into ChipboxTheme.
                implementation(projects.cbox.common.settings.api)
                // Needed for `GamesForPlatformDeepScreen(val platform: Platform)` — the typed
                // Voyager Screen that carries the route arg now that AndroidX nav's typesafe
                // routing is gone.
                implementation(projects.cbox.common.models.api)

                implementation(projects.features.library.api)
                api(projects.features.library.real)
                implementation(projects.features.manageLibrary.api)
                api(projects.features.manageLibrary.real)
                implementation(projects.features.rescanStatus.api)
                api(projects.features.rescanStatus.real)
                implementation(projects.features.nowPlaying.api)
                api(projects.features.nowPlaying.real)
                // Re-added after playback-status was ported to sage.kmp. The old debug=real/
                // release=fake variant split (which Metro 1.1.1 couldn't aggregate) is gone:
                // the screen is plain `sage.kmp` and the debug gate now lives in Settings'
                // `shouldShowDebug` section, not a build variant. `api(real)` so its
                // @ContributesIntoMap VM aggregates into ChipboxAppGraph / JvmChipboxGraph.
                implementation(projects.features.playbackStatus.api)
                api(projects.features.playbackStatus.real)
                implementation(projects.features.search.api)
                api(projects.features.search.real)
                implementation(projects.features.settings.api)
                api(projects.features.settings.real)
                implementation(projects.features.browseAllTracks.api)
                api(projects.features.browseAllTracks.real)
                implementation(projects.features.browseByArtist.api)
                api(projects.features.browseByArtist.real)
                implementation(projects.features.browseByGame.api)
                api(projects.features.browseByGame.real)
                implementation(projects.features.browseByPlatform.api)
                api(projects.features.browseByPlatform.real)
                implementation(projects.features.gamesForPlatform.api)
                api(projects.features.gamesForPlatform.real)
                implementation(projects.features.gameDetail.api)
                api(projects.features.gameDetail.real)
                implementation(projects.features.artistDetail.api)
                api(projects.features.artistDetail.real)

                // `sage.di.android` plugin auto-included this; the sage.kmp + metro combo
                // doesn't, so it's explicit here. Needed for `ChipboxAppUiViewModel`'s
                // `@ContributesIntoMap(AppScope::class)`.
                implementation(libs.sage.common.di)
                implementation(libs.sage.android.ui.list)
                implementation(libs.jetbrains.compose.material.icons.extended)
                implementation(libs.jetbrains.compose.material3.adaptive.navigation.suite)

                // Voyager — single Compose Multiplatform nav stack shared with apps/jvm.
                // Replaces androidx.navigation:navigation-compose (the AndroidX CMP fork
                // publishes JVM stubs only). Three artifacts: navigator (per-stack push/pop),
                // tab-navigator (per-tab back stacks for Library/Search/Settings —
                // saveState + restoreState equivalent), transitions (SlideTransition).
                implementation(libs.voyager.navigator)
                implementation(libs.voyager.tab.navigator)
                implementation(libs.voyager.transitions)
                implementation(libs.metrox.viewmodel)
                implementation(libs.metrox.viewmodel.compose)
            }
        }
    }
}
