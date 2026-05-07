plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.emulators.twosf"

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    api(projects.cbox.common.player.common)
    api(projects.cbox.common.player.emulators)
    implementation(projects.cbox.common.repository)
}
