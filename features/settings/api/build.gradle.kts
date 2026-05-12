plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "net.sigmabeta.chipbox.features.settings.api"
}

dependencies {
    implementation(libs.kotlinx.serialization.core)
}
