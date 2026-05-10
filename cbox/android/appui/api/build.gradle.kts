plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.compose.android)
    alias(libs.plugins.sage.di.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "net.sigmabeta.chipbox.appui"
}

dependencies {
    implementation(projects.cbox.android.ui.theme.api)
    implementation(projects.cbox.common.strings.api)
    implementation(projects.features.library)

    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.lifecycle.viewModelCompose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.core)
}
