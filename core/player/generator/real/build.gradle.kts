plugins {
    id("sage.android")
}

android {
    namespace = "net.sigmabeta.chipbox.player.generator.real"
}

dependencies {
    api(projects.core.player.generator)
    api(projects.core.player.emulators)
    implementation(projects.core.player.speaker)
    implementation("com.jakewharton.timber:timber:5.0.1")
}
