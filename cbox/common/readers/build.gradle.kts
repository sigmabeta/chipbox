plugins {
    alias(libs.plugins.sage.jvm)
}

dependencies {
    api(projects.core.repository)

    implementation(projects.cbox.common.models)
    implementation(projects.core.utils)
}
