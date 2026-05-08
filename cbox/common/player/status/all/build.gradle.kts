plugins {
    alias(libs.plugins.sage.jvm)
}

dependencies {
    api(projects.cbox.common.player.status.api)
    api(projects.cbox.common.player.status.real)
}
