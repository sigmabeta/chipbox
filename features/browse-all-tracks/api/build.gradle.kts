plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "net.sigmabeta.chipbox.features.browsealltracks.api"
}

dependencies {
    implementation(libs.kotlinx.serialization.core)
}
