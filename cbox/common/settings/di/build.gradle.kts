plugins {
    alias(libs.plugins.sage.jvm)
    alias(libs.plugins.sage.di.jvm)
}

dependencies {
    api(projects.cbox.common.settings.api)
    api(projects.cbox.common.settings.real)

    implementation(libs.sage.common.storage.common)
}
