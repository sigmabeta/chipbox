plugins {
    alias(libs.plugins.sage.jvm)
}

dependencies {
    api(projects.cbox.common.settings.api)

    implementation(libs.sage.common.storage.common)
}
