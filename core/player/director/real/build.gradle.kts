plugins {
    id("sage.jvm")
}

dependencies {
    api(projects.core.player.director)
    implementation(projects.cbox.common.player.common)
    implementation(projects.core.player.generator)
    implementation(projects.core.player.speaker)
    implementation(projects.cbox.common.repository)
}
