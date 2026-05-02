plugins {
    id("sage.jvm")
}

dependencies {
    api(projects.core.models)
    api(libs.kotlinx.coroutines.core)
}
