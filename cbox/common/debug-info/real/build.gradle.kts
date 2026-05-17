plugins {
    alias(libs.plugins.sage.jvm)
}

dependencies {
    api(projects.cbox.common.debugInfo.api)

    implementation(projects.cbox.common.models.api)
    implementation(projects.cbox.common.player.common.api)
    implementation(projects.cbox.common.player.director.api)
    implementation(projects.cbox.common.player.generator.api)
    implementation(projects.cbox.common.player.speaker.api)
    implementation(projects.cbox.common.player.buffer.api)
    implementation(libs.kotlinx.coroutines.core)
}
