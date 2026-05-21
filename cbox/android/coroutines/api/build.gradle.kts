import net.sigmabeta.sage.plugins.components.chipboxNamespace

plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = chipboxNamespace()
}

dependencies {
    api(libs.sage.common.list)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.sage.common.coroutines)
}
