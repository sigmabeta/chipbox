plugins {
    alias(libs.plugins.sage.jvm)
    alias(libs.plugins.sage.di)
}

dependencies {
    api(projects.cbox.common.player.persistence.api)
    api(projects.cbox.common.player.persistence.real)

    implementation(projects.cbox.common.player.director.api)
    implementation(libs.sage.common.storage.common)
    implementation(libs.sage.common.logging)
}
