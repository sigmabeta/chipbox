plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.contentsource.file.di"
}

dependencies {
    api(projects.cbox.android.contentsource.file.api)
    api(projects.cbox.android.contentsource.file.real)
}
