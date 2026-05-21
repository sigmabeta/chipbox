plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.compose.android)
    alias(libs.plugins.sage.di.android)
    alias(libs.plugins.metro)
}

android {
    namespace = "net.sigmabeta.chipbox.features.settings.real"
}

// SettingsViewModel is the first @HiltViewModel converted to Metro (M5c — see
// docs/metro-migration.md). It carries `@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())`
// and SettingsRoute consumes it via `metroViewModel<SettingsViewModel>()`.
// interop.includeDagger() keeps Hilt's @HiltViewModel processing working in OTHER
// feature modules across the compile classpath until their own per-feature M5c
// slices flip them over.
metro {
    interop {
        includeDagger()
    }
}

dependencies {
    api(projects.features.settings.api)

    implementation(projects.cbox.android.contentsource.file.real)
    implementation(projects.cbox.android.ui.list.api)
    implementation(libs.sage.common.di)
    implementation(projects.cbox.common.ui.fonts.api)
    implementation(projects.cbox.common.appcomm.api)
    // features.playbackStatus.api dropped during Hilt → Metro migration —
    // PlaybackStatusEntryPoint pulled in playback-status' Hilt module transitively, and
    // Metro's @ContributesTo aggregation didn't see it through the variant-specific
    // debugImplementation chain. Re-add when playback-status itself migrates. See
    // docs/metro-migration.md.
    implementation(projects.cbox.common.strings.api)
    implementation(projects.cbox.common.settings.api)
    implementation(projects.cbox.common.debug.api)
    implementation(projects.cbox.common.repository.api)
    implementation(projects.cbox.common.scanner.api)

    implementation(libs.sage.common.appinfo)
    implementation(libs.sage.common.ui.components)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.androidx.activity.compose)

    implementation(libs.metrox.viewmodel)
    implementation(libs.metrox.viewmodel.compose)
}
