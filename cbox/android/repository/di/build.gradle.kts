plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di)
}

android {
    namespace = "net.sigmabeta.chipbox.repository.di"
}

dependencies {
    api(projects.cbox.common.repository.api)
    api(projects.cbox.common.repository.real)
    // ChipboxDatabase (Room @Database) — this module extracts its DAOs to construct
    // DatabaseRepository. repository/real now depends only on database/api (the DAO interfaces),
    // so the concrete Room database is pulled in here, at the DI seam.
    implementation(projects.cbox.common.database.real)
    // Memory + Random repositories and the debug setting that selects between them and the DB.
    implementation(projects.cbox.common.repository.fake)
    implementation(projects.cbox.common.debug.api)
}
