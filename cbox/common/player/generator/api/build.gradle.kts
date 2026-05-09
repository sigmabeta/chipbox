plugins {
    alias(libs.plugins.sage.jvm)
}

dependencies {
    api(projects.cbox.common.player.common.api)
    api(projects.cbox.common.player.buffer.all)
    api(projects.cbox.common.player.cache.api)
    api(projects.cbox.common.repository.api)
    api(projects.cbox.common.contentsource.api)
    api(libs.kotlinx.coroutines.core)
    api(libs.sage.common.logging)
}
