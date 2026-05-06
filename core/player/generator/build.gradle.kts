plugins {
    id("sage.jvm")
}

dependencies {
    api(projects.cbox.common.player.common)
    api(projects.cbox.common.repository)
    api(projects.core.player.buffer)
    api(projects.cbox.common.contentsource)
    api(libs.kotlinx.coroutines.core)
    api(libs.sage.common.logging)
}
