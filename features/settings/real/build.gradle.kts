plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.compose.android)
    alias(libs.plugins.sage.di.android)
    alias(libs.plugins.metro)
}

android {
    namespace = "net.sigmabeta.chipbox.features.settings.real"
}

// First feature module to register a Metro-side @Inject @ViewModelKey @ContributesIntoMap
// VM (SettingsViewModel). interop.includeDagger() keeps Hilt's @HiltViewModel processing
// working in OTHER feature modules across the compile classpath; SettingsViewModel itself
// drops @HiltViewModel and goes Metro-native.
metro {
    interop {
        includeDagger()
    }
}

dependencies {
    api(projects.features.settings.api)

    implementation(projects.cbox.android.contentsource.file.real)
    implementation(projects.cbox.android.ui.list.api)
    implementation(projects.cbox.common.di.api)
    implementation(projects.cbox.common.ui.fonts.api)
    implementation(projects.cbox.common.appcomm.api)
    implementation(projects.features.playbackStatus.api)
    implementation(projects.cbox.common.strings.api)
    implementation(projects.cbox.common.settings.api)
    implementation(projects.cbox.common.debug.api)
    implementation(projects.cbox.common.repository.api)
    implementation(projects.cbox.common.scanner.api)

    implementation(libs.sage.common.appinfo)
    implementation(libs.sage.common.ui.components)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.metrox.viewmodel)
    implementation(libs.metrox.viewmodel.compose)
}
