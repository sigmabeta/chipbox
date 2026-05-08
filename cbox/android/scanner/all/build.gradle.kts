plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.scanner.all"
}

dependencies {
    api(projects.cbox.android.scanner.api)
    api(projects.cbox.android.scanner.real)
    api(projects.cbox.android.scanner.fake)
}
