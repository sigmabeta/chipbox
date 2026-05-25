import net.sigmabeta.sage.plugins.components.namespaceFromPath

plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di)
}

android {
    namespace = namespaceFromPath()
}

dependencies {
    api(libs.sage.common.storage.common)

    implementation(libs.androidx.dataStore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.sage.common.coroutines)
    implementation(libs.sage.common.logging)
}
