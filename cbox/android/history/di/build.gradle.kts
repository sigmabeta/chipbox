plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di)
}

android {
    namespace = "net.sigmabeta.chipbox.history.di"
}

dependencies {
    api(projects.cbox.common.history.api)
    api(projects.cbox.common.history.real)
}
