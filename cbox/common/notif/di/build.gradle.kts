plugins {
    alias(libs.plugins.sage.jvm)
    alias(libs.plugins.sage.di)
}

dependencies {
    api(projects.cbox.common.notif.api)
    api(projects.cbox.common.notif.real)

    implementation(projects.cbox.common.scanner.api)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.sage.common.logging)
    implementation(libs.sage.common.storage.common)
    implementation(libs.sage.common.ui.strings)
}
