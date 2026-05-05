plugins {
    id("sage.android")
    id("sage.compose.android")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "net.sigmabeta.chipbox.features.artists"
}

dependencies {
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)

    implementation(projects.core.components)
    implementation(projects.cbox.common.models)
    implementation(projects.cbox.common.repository)

    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material")
    implementation("com.google.accompanist:accompanist-insets:0.23.1")
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation("com.jakewharton.timber:timber:5.0.1")
}
