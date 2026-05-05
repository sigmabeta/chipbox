plugins {
    alias(libs.plugins.sage.jvm)
}

dependencies {
    api(projects.cbox.common.models)
    api(libs.kotlinx.coroutines.core)
}
