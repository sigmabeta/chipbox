plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.contentsource.file.all"
}

dependencies {
    api(projects.cbox.common.contentsource.file.real)
}
