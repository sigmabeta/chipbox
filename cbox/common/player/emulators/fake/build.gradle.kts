plugins {
    alias(libs.plugins.sage.jvm)
}

dependencies {
    api(projects.cbox.common.player.emulators.api)
    api(libs.kotlinx.coroutines.core)

    implementation(projects.cbox.common.repository.api)
}
