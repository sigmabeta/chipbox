plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.repository.di"
}

dependencies {
    api(projects.cbox.common.repository.api)
    api(projects.cbox.common.repository.real)
}
