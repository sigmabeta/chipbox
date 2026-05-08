plugins {
    alias(libs.plugins.sage.jvm)
}

dependencies {
    api(projects.cbox.common.repository.api)

    implementation(projects.cbox.common.models.api)
    implementation(projects.cbox.common.utils.api)
    implementation(libs.sage.common.logging)
}
