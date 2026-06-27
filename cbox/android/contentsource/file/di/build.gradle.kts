plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di)
}

android {
    namespace = "net.sigmabeta.chipbox.contentsource.file.di"
}

dependencies {
    api(projects.cbox.common.contentsource.file.real)
    // okio.FileSystem (bound to FileSystem.SYSTEM in AndroidAppModule) + File.toOkioPath() to build
    // the locations-file Path that LocalFileContentSource now takes.
    implementation(libs.okio)
}
