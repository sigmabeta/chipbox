plugins {
    alias(libs.plugins.sage.jvm)
}

dependencies {
    api(projects.cbox.common.models.api)
    api(projects.cbox.common.player.buffer.api)

    implementation(libs.kotlinx.coroutines.core)
}
