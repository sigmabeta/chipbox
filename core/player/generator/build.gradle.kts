plugins {
    id("sage.jvm")
}

dependencies {
    api(projects.core.player.common)
    api(projects.core.repository)
    api(projects.core.player.buffer)
    api(projects.cbox.common.contentsource)
    api(libs.kotlinx.coroutines.core)
    api(libs.sage.common.logging)
}
