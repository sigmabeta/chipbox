plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.repository.real"
}

dependencies {
    api(projects.cbox.common.repository.api)
    api(projects.cbox.android.database.all)
}
