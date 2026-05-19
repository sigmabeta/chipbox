plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.player.emulators.vgm.all"
    }

    sourceSets {
        named("jvmSharedMain") {
            dependencies {
                api(projects.cbox.android.player.emulators.vgm.api)
                api(projects.cbox.android.player.emulators.vgm.real)
            }
        }
    }
}
