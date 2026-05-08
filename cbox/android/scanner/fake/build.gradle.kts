plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.scanner.fake"
}

dependencies {
    api(projects.cbox.common.scanner.api)

    implementation(projects.cbox.android.repository.all)
}
