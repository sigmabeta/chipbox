plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.repository.fake"
}

dependencies {
    api(projects.cbox.common.repository.api)
    api(libs.sage.common.ui.strings)

    implementation(libs.sage.common.logging)
}
