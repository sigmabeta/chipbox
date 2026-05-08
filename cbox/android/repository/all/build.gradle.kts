plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.repository.all"
}

dependencies {
    api(projects.cbox.android.repository.api)
    api(projects.cbox.android.repository.real)
    api(projects.cbox.android.repository.fake)
}
