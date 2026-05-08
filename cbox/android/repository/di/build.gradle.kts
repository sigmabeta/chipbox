plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.repository.di"
}

dependencies {
    api(projects.cbox.android.repository.api)
    api(projects.cbox.android.repository.real)
    api(projects.cbox.android.repository.fake)
}
