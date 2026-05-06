plugins {
    id("sage.jvm")
}

dependencies {
    api(projects.cbox.common.player.director)
    implementation(projects.cbox.common.player.common)
    implementation(projects.core.player.generator)
    implementation(projects.cbox.common.player.speaker)
    implementation(projects.cbox.common.repository)
}
