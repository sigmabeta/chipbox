plugins {
    alias(libs.plugins.sage.android)
}

dependencies {
    api(projects.cbox.common.contentsource)
}

android {
    namespace = "net.sigmabeta.chipbox.contentsource.file"
}