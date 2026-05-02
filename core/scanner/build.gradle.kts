plugins {
    id("sage.android")
}

android {
    namespace = "net.sigmabeta.chipbox.scanner"
}

dependencies {
    api(projects.core.models)
    api(libs.kotlinx.coroutines.core)
    api("com.jakewharton.timber:timber:5.0.1")
}
