plugins {
    alias(libs.plugins.sage.jvm)
}

dependencies {
    api(libs.sage.common.coroutines)
    implementation(libs.hilt.core)
}
