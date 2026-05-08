plugins {
    alias(libs.plugins.sage.jvm)
}

dependencies {
    api(projects.cbox.common.models.api)
    api(projects.cbox.common.player.common.api)
    api(libs.kotlinx.coroutines.core)
    api(libs.sage.common.logging)
}
