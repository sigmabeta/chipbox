plugins {
    id("sage.android")
    id("sage.compose.android")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "net.sigmabeta.chipbox.activities"
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)

    api(projects.cbox.android.navigation)

    implementation(projects.cbox.android.components)
    implementation(projects.cbox.android.drawables)
    implementation(projects.cbox.android.services)
    implementation(projects.cbox.android.strings)
    implementation(projects.cbox.android.styles)
    implementation(projects.cbox.common.scanner)

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

    // Just for now!!
    implementation(projects.cbox.android.contentsource.file.di)
}
