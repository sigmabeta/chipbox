plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.repository.all"
}

dependencies {
    api(projects.cbox.common.repository.api)
    api(projects.cbox.common.repository.real)
}
