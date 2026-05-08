plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.database.di"
}

dependencies {
    api(projects.cbox.android.database.api)
    api(projects.cbox.android.database.real)
}
