plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.player.emulators.all"
    }

    sourceSets {
        named("jvmSharedMain") {
            dependencies {
                api(projects.cbox.common.player.emulators.api)
                api(projects.cbox.common.player.emulators.fake)
            }
        }
    }
}
