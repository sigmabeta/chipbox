plugins {
    alias(libs.plugins.sage.jvm)
    alias(libs.plugins.sage.di.jvm)
}

dependencies {
    api(projects.cbox.common.repository.api)
    api(projects.cbox.common.repository.fake)
}
