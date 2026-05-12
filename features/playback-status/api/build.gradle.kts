plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "net.sigmabeta.chipbox.features.playbackstatus.api"
}

dependencies {
    implementation(projects.cbox.common.appcomm.api)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.androidx.navigation.compose)
}
