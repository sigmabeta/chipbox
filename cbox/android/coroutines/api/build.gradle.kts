plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.coroutines"
}

dependencies {
    api(libs.sage.common.list)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.sage.common.coroutines)
}
