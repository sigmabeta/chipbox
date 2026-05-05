plugins {
    id("sage.android")
}

android {
    namespace = "net.sigmabeta.chipbox.player.emulators.twosf"

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

dependencies {
    api(projects.core.player.common)
    api(projects.core.player.emulators)
    implementation(projects.cbox.common.repository)
}
