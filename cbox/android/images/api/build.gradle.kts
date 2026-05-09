plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.images"
}

dependencies {
    api(libs.coil.kt.core)
    api(libs.coil.kt.compose)
    api(libs.coil.kt.okhttp)

    api(libs.sage.android.bitmaps)

    implementation(libs.sage.common.analytics)
    implementation(libs.sage.common.images)
}
