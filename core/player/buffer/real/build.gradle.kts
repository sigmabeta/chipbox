plugins {
    id("sage.jvm")
}

dependencies {
    api(projects.core.player.buffer)
    implementation(projects.core.player.common)
}
