plugins {
    id("sage.jvm")
}

dependencies {
    api(projects.core.player.generator)
    implementation(projects.core.player.emulators.fake)
}
