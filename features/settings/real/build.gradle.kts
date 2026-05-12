plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.compose.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.features.settings.real"
}

dependencies {
    api(projects.features.settings.api)

    implementation(projects.cbox.android.contentsource.file.real)
    implementation(projects.cbox.android.ui.list.api)
    implementation(projects.cbox.android.ui.fonts.api)
    implementation(projects.cbox.common.appcomm.api)
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
}
