plugins {
    alias(libs.plugins.sage.jvm)
}

dependencies {
    api(projects.cbox.common.repository.api)
    api(projects.cbox.common.repository.fake)
}
