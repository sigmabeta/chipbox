plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.scanner.all"
}

dependencies {
    api(projects.cbox.common.scanner.api)
    api(projects.cbox.common.scanner.real)
}
