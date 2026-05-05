plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "net.sigmabeta.chipbox.database"
}

dependencies {
    api(projects.cbox.common.entities)

    api(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
}
