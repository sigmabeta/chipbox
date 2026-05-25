plugins {
    alias(libs.plugins.sage.jvm)
    alias(libs.plugins.sage.di)
}

dependencies {
    api(projects.cbox.common.player.director.real)

    implementation(projects.cbox.common.player.generator.api)
    implementation(projects.cbox.common.player.speaker.api)
}
