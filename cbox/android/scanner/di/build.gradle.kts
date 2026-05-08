plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.scanner.di"
}

dependencies {
    api(projects.cbox.android.scanner.api)
    api(projects.cbox.android.scanner.real)
    api(projects.cbox.android.scanner.fake)
}
