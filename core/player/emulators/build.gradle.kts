plugins {
    id("sage.jvm")
}

dependencies {
    api(projects.core.models)
    api(projects.core.player.common)
    api(libs.kotlinx.coroutines.core)
    api(libs.sage.common.logging)
}
