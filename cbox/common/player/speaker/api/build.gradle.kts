plugins {
    alias(libs.plugins.sage.jvm)
}

dependencies {
    api(projects.cbox.common.player.common.api)
    api(projects.cbox.common.player.buffer.api)
    api(libs.kotlinx.coroutines.core)
}
