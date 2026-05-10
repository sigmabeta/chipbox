plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "net.sigmabeta.chipbox.features.library.api"
}

dependencies {
    implementation(libs.kotlinx.serialization.core)
}
