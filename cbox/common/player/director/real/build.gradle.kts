plugins {
    alias(libs.plugins.sage.jvm)
}

dependencies {
    api(projects.cbox.common.player.director.api)

    implementation(projects.cbox.common.player.common.api)
    implementation(projects.cbox.common.player.generator.api)
    implementation(projects.cbox.common.player.speaker.api)
    implementation(projects.cbox.common.repository.api)
    implementation(libs.sage.common.logging)
}
