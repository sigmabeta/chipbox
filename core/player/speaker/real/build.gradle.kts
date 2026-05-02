plugins {
    id("sage.android")
}

android {
    namespace = "net.sigmabeta.chipbox.player.speaker.real"
}

dependencies {
    api(projects.core.player.speaker)
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("androidx.media2:media2-common:1.3.0")
}
