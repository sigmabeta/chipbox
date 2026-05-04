plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

dependencies {
    api(projects.cbox.android.contentsource.file)
}

android {
    namespace = "net.sigmabeta.chipbox.contentsource.file.di"
}