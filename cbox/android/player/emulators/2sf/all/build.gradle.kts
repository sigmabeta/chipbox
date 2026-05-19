plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.player.emulators.twosf.all"
    }

    sourceSets {
        named("jvmSharedMain") {
            dependencies {
                api(projects.cbox.android.player.emulators.twosf.api)
                api(projects.cbox.android.player.emulators.twosf.real)
            }
        }
    }
}
