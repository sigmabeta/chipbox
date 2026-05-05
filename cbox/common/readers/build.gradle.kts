plugins {
    alias(libs.plugins.sage.jvm)
}

dependencies {
    api(projects.core.repository)

    implementation(projects.core.models)
    implementation(projects.core.utils)
}
