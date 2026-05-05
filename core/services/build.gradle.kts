plugins {
    id("sage.android")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "net.sigmabeta.chipbox.services"
}

dependencies {
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)

    api("androidx.media2:media2-common:1.3.0")

    implementation(projects.core.colors)
    implementation(projects.core.drawables)
    implementation(projects.core.player.common)
    implementation(projects.core.player.director)
    implementation(projects.cbox.common.repository)
    implementation(projects.core.strings)
    implementation("com.jakewharton.timber:timber:5.0.1")
}
