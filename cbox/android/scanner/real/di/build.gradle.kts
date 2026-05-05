plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.scanner.real.di"
}

dependencies {
    api(projects.cbox.android.scanner.real)
}
