plugins {
    id("sage.jvm")
}

dependencies {
    api(projects.core.player.common)
    api(projects.core.player.emulators)
    api(libs.kotlinx.coroutines.core)
    implementation(projects.core.repository)
}
