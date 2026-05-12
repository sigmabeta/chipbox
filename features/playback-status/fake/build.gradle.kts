plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.features.playbackstatus.fake"
}

dependencies {
    api(projects.features.playbackStatus.api)

    implementation(projects.cbox.common.appcomm.api)
    implementation(libs.androidx.navigation.compose)
}
