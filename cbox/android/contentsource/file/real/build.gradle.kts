plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.contentsource.file.real"
}

dependencies {
    api(projects.cbox.common.contentsource.api)
}
