plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.player.emulators.fake"
    }

    sourceSets {
        named("jvmSharedMain") {
            dependencies {
                api(projects.cbox.common.player.emulators.api)
                api(libs.kotlinx.coroutines.core)

                implementation(projects.cbox.common.repository.api)
            }
        }
    }
}
