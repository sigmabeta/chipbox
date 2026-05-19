plugins {
    alias(libs.plugins.sage.jvm)
}

dependencies {
    api(projects.cbox.common.player.generator.api)
    api(projects.cbox.common.player.emulators.api)
    api(projects.cbox.common.player.cache.real)
}
