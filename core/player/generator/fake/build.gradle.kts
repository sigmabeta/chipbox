plugins {
    id("sage.jvm")
}

dependencies {
    api(projects.core.player.generator)
    implementation(projects.cbox.common.player.emulators.fake)
}
