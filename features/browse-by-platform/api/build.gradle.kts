plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "net.sigmabeta.chipbox.features.browsebyplatform.api"
}

dependencies {
    implementation(libs.kotlinx.serialization.core)
}
