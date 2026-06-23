import net.sigmabeta.sage.plugins.components.namespaceFromPath

plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
    alias(libs.plugins.sage.compose.kmp)
    alias(libs.plugins.metro)
    alias(chipbox.plugins.kmp.test)
}

// Promoted from sage.android → sage.kmp so the same `ChipboxAppUi()` composable drives the
// Android `MainActivity` and the JVM `DesktopMain.kt`. Voyager + JetBrains-Compose `material3-
// adaptive-navigation-suite` are both cross-platform; the Android-only pieces of the old
// `buildOuterSink` (Intent.ACTION_VIEW, ClipboardManager) became platform-callback parameters
// in [ChipboxAppUi], filled in by each app entry point.
kotlin {
    android {
        namespace = namespaceFromPath()
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
                // LibraryViewModel started crashing at runtime with `Unknown model class`.
                api(projects.cbox.common.playerStatus.api)
                implementation(projects.cbox.common.ui.chrome.api)
                implementation(projects.cbox.common.ui.components.api)
                implementation(projects.cbox.common.ui.list.api)
                // Reads the platform LocalLifecycleOwner to feed LocalScreenLifecycleOwner (the
                // owner the screen entries observe for foreground/background).
                implementation(libs.androidx.lifecycle.runtimeCompose)
                implementation(projects.cbox.common.appcomm.api)
                implementation(projects.cbox.common.strings.api)
                // Shared `ChipboxTheme()` — multiplatform. The Android-side `AppTheme()`
                // wrapper in `cbox/android/ui/theme/api` was thin syntactic sugar and is
                // no longer referenced from this module.
                implementation(projects.cbox.common.ui.theme.api)
                // ChipboxSettingsManager + ThemeMode — read by ChipboxAppUiViewModel to pick
                // the light/dark color scheme passed into ChipboxTheme.
                implementation(projects.cbox.common.settings.api)
                // DebugSettingsManager + ImageLoaderSource — read by ChipboxAppUiViewModel to drive
                // LocalForceFakeImages (the debug "image loader = fake" switch).
                implementation(projects.cbox.common.debug.api)
                // AppInfo.isDebug — read by ChipboxAppUiViewModel so debug builds swap the
                // primary/secondary palette (ChipboxTheme.swapPrimaryAndSecondary).
                implementation(libs.sage.common.appinfo)
                // Needed for `GamesForPlatformDeepScreen(val platform: Platform)` — the typed
                // Voyager Screen that carries the route arg now that AndroidX nav's typesafe
                // routing is gone.
                implementation(projects.cbox.common.models.api)

                // Each feature's `:real` already `api()`s its own `:api`, so depending on `:real`
                // alone transitively exposes the `:api` nav markers (Home, GameDetail, …) that
                // ChipboxScreens references. `api(real)` (not implementation) so every feature's
                // @ContributesIntoMap VM aggregates into ChipboxAppGraph / JvmChipboxGraph.
                api(projects.features.home.real)
                api(projects.features.library.real)
                api(projects.features.folderPicker.real)
                api(projects.features.manageLibrary.real)
                api(projects.features.rescanStatus.real)
                api(projects.features.nowPlaying.real)
                // playback-status is plain `sage.kmp` now (the old debug=real/release=fake variant
                // split Metro 1.1.1 couldn't aggregate is gone); the debug gate lives in Settings'
                // `shouldShowDebug` section, not a build variant.
                api(projects.features.playbackStatus.real)
                // Debug-only error log reached from that same Settings debug section.
                api(projects.features.errorLog.real)
                // Debug-only crash log (persisted fatal exceptions), same Settings debug section.
                api(projects.features.crashLog.real)
                // Debug-only component gallery, reached from the same Settings debug section.
                api(projects.features.componentLibrary.real)
                api(projects.features.search.real)
                api(projects.features.settings.real)
                api(projects.features.browseAllTracks.real)
                api(projects.features.browseByArtist.real)
                api(projects.features.browseByGame.real)
                api(projects.features.browseByPlatform.real)
                api(projects.features.favorites.real)
                api(projects.features.playlists.real)
                api(projects.features.playlistDetail.real)
                api(projects.features.gamesForPlatform.real)
                api(projects.features.gameDetail.real)
                api(projects.features.artistDetail.real)

                // `sage.di.android` plugin auto-included this; the sage.kmp + metro combo
                // doesn't, so it's explicit here. Needed for `ChipboxAppUiViewModel`'s
                // `@ContributesIntoMap(AppScope::class)`.
                implementation(libs.sage.common.di)
                implementation(libs.sage.common.ui.listScreens)
                implementation(libs.sage.common.ui.iconsReal)
                implementation(libs.jetbrains.compose.material3.adaptive.navigation.suite)

                // Voyager — single Compose Multiplatform nav stack shared with apps/jvm.
                // Replaces androidx.navigation:navigation-compose (the AndroidX CMP fork
                // publishes JVM stubs only). Four artifacts: navigator (per-stack push/pop),
                // tab-navigator (per-tab back stacks for Home/Library/Search —
                // saveState + restoreState equivalent), transitions (SlideTransition),
                // screenmodel (a back-stack-scoped holder used on JVM/JS to keep each Screen's
                // ViewModelStore alive across navigation — see PerScreenViewModelStore.jvm.kt).
                implementation(libs.voyager.navigator)
                implementation(libs.voyager.tab.navigator)
                implementation(libs.voyager.transitions)
                implementation(libs.voyager.screenmodel)
                implementation(libs.metrox.viewmodel)
                implementation(libs.metrox.viewmodel.compose)
            }
        }

        named("commonTest") {
            dependencies {
                implementation(projects.cbox.common.settings.fake)
                implementation(projects.cbox.common.debug.fake)
            }
        }
    }
}

// appui's commonMain has four top-level `staticCompositionLocalOf<MaterialType>` declarations
// (LocalActiveTabNavigator, LocalAppActionSink, LocalAppSnackbarHostState, LocalPlatformBackKeys).
// Those force the Material3 / Compose UI runtime to link at class-init on Kotlin/JS — which
// drags Skiko, whose JS distribution ships only browser/WASM builds, not a Node loader. The
// VM is fully covered by jvmTest; jsTest on every other module guards the cross-platform
// contract. Disable jsTest here rather than restructure the host-UI module.
//
// Today ChipboxAppUiViewModel just stitches together three settings flows — small enough that
// jvm coverage alone is fine. If it ever grows real logic (route stack reducers, app-lifecycle
// gating, anything subtle enough that JS-vs-JVM divergence could bite), carve the VM out into
// its own pure-Kotlin sibling (`cbox/common/appui/vm`) so it can be tested on JS too.
tasks.matching { it.name == "jsTest" || it.name == "jsNodeTest" }.configureEach { enabled = false }
