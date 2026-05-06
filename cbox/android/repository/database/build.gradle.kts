plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.repository.database"
}

dependencies {
    api(projects.cbox.common.repository)
    api(projects.cbox.android.database)
}
