import net.sigmabeta.sage.plugins.components.namespaceFromPath

plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di)
}

android {
    namespace = namespaceFromPath()
}

dependencies {
    api(libs.coil.kt.core)
    api(libs.coil.kt.compose)
    api(libs.coil.kt.okhttp)

    implementation(libs.androidx.core.ktx)
    implementation(libs.sage.common.analytics)
    implementation(libs.sage.common.images)
}
