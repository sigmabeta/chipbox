plugins {
    alias(libs.plugins.sage.jvm)
}

dependencies {
    api(projects.cbox.common.player.director.api)
    api(projects.cbox.common.player.director.real)
}
