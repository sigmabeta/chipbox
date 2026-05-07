plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.repository.mock"
}

dependencies {
    api(projects.cbox.common.repository)

    implementation(libs.sage.common.logging)
    api(libs.sage.common.ui.strings)
}
