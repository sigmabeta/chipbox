plugins {
    alias(libs.plugins.sage.jvm)
    alias(libs.plugins.sage.di)
}

dependencies {
    api(projects.cbox.common.favorites.api)
    api(projects.cbox.common.favorites.real)

    // The debug switch picks Real vs Fake at graph build.
    implementation(projects.cbox.common.favorites.fake)
    implementation(projects.cbox.common.debug.api)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.sage.common.logging)
}
