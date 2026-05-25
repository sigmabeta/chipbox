plugins {
    alias(libs.plugins.sage.jvm)
    alias(libs.plugins.sage.di)
}

dependencies {
    api(projects.cbox.common.debugInfo.api)
    api(projects.cbox.common.debugInfo.real)

    implementation(projects.cbox.common.player.director.api)
    implementation(projects.cbox.common.player.generator.api)
    implementation(projects.cbox.common.player.speaker.api)
    implementation(projects.cbox.common.player.buffer.api)
    implementation(libs.kotlinx.coroutines.core)
}
