plugins {
    alias(libs.plugins.sage.jvm)
}

dependencies {
    api(projects.cbox.common.player.director.api)
    api(libs.sage.common.logging)

    implementation(projects.cbox.common.player.common.api)
    implementation(projects.cbox.common.player.generator.api)
    implementation(projects.cbox.common.player.speaker.api)
    implementation(projects.cbox.common.repository.api)
}
