plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.player.emulators.gme.all"
    }

    sourceSets {
        named("jvmSharedMain") {
            dependencies {
                api(projects.cbox.android.player.emulators.gme.api)
                api(projects.cbox.android.player.emulators.gme.real)
            }
        }
    }
}
