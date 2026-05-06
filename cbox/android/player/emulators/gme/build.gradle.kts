plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.emulators.gme"

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

dependencies {
    api(projects.cbox.common.player.common)
    api(projects.cbox.common.player.emulators)
    implementation(projects.cbox.common.repository)
}
