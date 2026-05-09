plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.compose.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.activities"
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    api(projects.cbox.android.navigation.api)

    implementation(projects.cbox.android.components.api)
    implementation(projects.cbox.android.contentsource.file.real)
    implementation(projects.cbox.android.drawables.api)
    implementation(projects.cbox.android.services.api)
    implementation(projects.cbox.android.strings.api)
    implementation(projects.cbox.android.styles.api)
    implementation(projects.cbox.common.scanner.api)

    implementation("androidx.media2:media2-common:1.3.0")
    implementation("com.google.accompanist:accompanist-insets:0.23.1")
    implementation("androidx.compose.material:material:1.4.3")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewModelCompose)
    implementation("androidx.compose.runtime:runtime-livedata:1.4.3")
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
}
