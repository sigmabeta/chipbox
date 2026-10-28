plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di)
}

android {
    namespace = "net.sigmabeta.chipbox.scanner.di"
}

dependencies {
    api(projects.cbox.common.scanner.api)
    api(projects.cbox.common.scanner.real)
    // scanner.real is now KMP and takes the platform-neutral LibrarySource interface;
    // the Android Hilt module here picks the concrete AndroidFileContentSource impl,
    // so it depends on contentsource:file:real directly (no longer pulled transitively).
    implementation(projects.cbox.common.contentsource.file.real)
}
