plugins {
    alias(libs.plugins.sage.jvm)
}

dependencies {
    api(projects.cbox.common.player.cache.api)
    api(projects.cbox.common.player.cache.real)
}
