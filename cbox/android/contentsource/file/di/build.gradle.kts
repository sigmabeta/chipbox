plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di)
}

android {
    namespace = "net.sigmabeta.chipbox.contentsource.file.di"
}

dependencies {
    api(projects.cbox.common.contentsource.file.real)
}
