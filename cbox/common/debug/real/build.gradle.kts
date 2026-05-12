plugins {
    alias(libs.plugins.sage.jvm)
}

dependencies {
    api(projects.cbox.common.debug.api)

    implementation(libs.sage.common.storage.common)
}
