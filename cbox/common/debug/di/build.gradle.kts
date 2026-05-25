plugins {
    alias(libs.plugins.sage.jvm)
    alias(libs.plugins.sage.di)
}

dependencies {
    api(projects.cbox.common.debug.api)
    api(projects.cbox.common.debug.real)

    implementation(libs.sage.common.storage.common)
}
