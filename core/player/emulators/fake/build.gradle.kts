plugins {
    id("sage.jvm")
}

dependencies {
    api(projects.cbox.common.player.common)
    api(projects.core.player.emulators)
    api(libs.kotlinx.coroutines.core)
    implementation(projects.cbox.common.repository)
}
