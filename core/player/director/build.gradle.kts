plugins {
    id("sage.jvm")
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(projects.core.models)
    implementation(projects.core.player.common)
}
