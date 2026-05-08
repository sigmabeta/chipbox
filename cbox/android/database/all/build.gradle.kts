plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.database.all"
}

dependencies {
    api(projects.cbox.android.database.api)
    api(projects.cbox.android.database.real)
}
