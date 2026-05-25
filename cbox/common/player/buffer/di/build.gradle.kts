plugins {
    alias(libs.plugins.sage.jvm)
    alias(libs.plugins.sage.di)
}

dependencies {
    api(projects.cbox.common.player.buffer.api)
    api(projects.cbox.common.player.buffer.real)
    implementation(libs.sage.common.logging)
}
