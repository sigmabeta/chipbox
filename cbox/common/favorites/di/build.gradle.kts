plugins {
    alias(libs.plugins.sage.jvm)
    alias(libs.plugins.sage.di)
}

dependencies {
    api(projects.cbox.common.favorites.api)
    api(projects.cbox.common.favorites.real)

    implementation(libs.sage.common.logging)
}
