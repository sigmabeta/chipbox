plugins {
    id("sage.jvm")
}

dependencies {
    api(projects.core.player.common)
    api(projects.core.repository)
    api(projects.core.player.buffer)
    api(libs.kotlinx.coroutines.core)
}
