plugins {
    alias(libs.plugins.sage.jvm)
}

dependencies {
    api(projects.cbox.common.player.director)
    api(libs.sage.common.logging)

    implementation(projects.cbox.common.player.common)
    implementation(projects.cbox.common.player.generator)
    implementation(projects.cbox.common.player.speaker)
    implementation(projects.cbox.common.repository)
}
