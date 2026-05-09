plugins {
    alias(libs.plugins.sage.jvm)
}

dependencies {
    api(projects.cbox.common.player.cache.api)
    api(projects.cbox.common.player.emulators.api)
    api(libs.sage.common.logging)

    implementation(projects.cbox.common.contentsource.api)
    implementation(projects.cbox.common.player.common.api)
    implementation(libs.kotlinx.coroutines.core)
}
