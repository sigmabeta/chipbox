plugins {
    id("sage.jvm")
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(projects.cbox.common.models)
    implementation(projects.cbox.common.player.common)
}
