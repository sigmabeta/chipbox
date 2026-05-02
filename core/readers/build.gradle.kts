plugins {
    id("sage.android")
}

android {
    namespace = "net.sigmabeta.chipbox.repository.readers"
}

dependencies {
    api(projects.core.repository)
    implementation(projects.core.models)
    implementation(projects.core.utils)
    implementation("com.jakewharton.timber:timber:5.0.1")
}
