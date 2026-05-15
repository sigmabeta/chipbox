plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "net.sigmabeta.chipbox.features.gamesforplatform.api"
}

dependencies {
    implementation(libs.kotlinx.serialization.core)
    implementation(projects.cbox.common.models.api)
}
