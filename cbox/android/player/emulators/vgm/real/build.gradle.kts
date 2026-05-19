plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.emulators.vgm.real"

    externalNativeBuild {
        cmake {
            path = rootProject.file("cbox/native/vgm/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    api(projects.cbox.common.player.common.api)
    api(projects.cbox.common.player.emulators.api)
    implementation(projects.cbox.common.repository.api)
}
