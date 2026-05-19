plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.player.emulators.ssf.all"
    }

    sourceSets {
        named("jvmSharedMain") {
            dependencies {
                api(projects.cbox.android.player.emulators.ssf.api)
                api(projects.cbox.android.player.emulators.ssf.real)
            }
        }
    }
}
