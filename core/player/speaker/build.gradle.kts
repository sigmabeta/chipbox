plugins {
    id("sage.jvm")
}

dependencies {
    api(projects.cbox.common.player.common)
    api(projects.cbox.common.player.buffer)
    api(libs.kotlinx.coroutines.core)
}
