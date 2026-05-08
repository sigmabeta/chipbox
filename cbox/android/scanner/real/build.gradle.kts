plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.scanner.real"
}

dependencies {
    api(projects.cbox.common.scanner.api)
    api(projects.cbox.common.repository.api)
    api(projects.cbox.android.contentsource.file.all)
    api(projects.cbox.common.readers.api)

    implementation(libs.sage.common.logging)
}
