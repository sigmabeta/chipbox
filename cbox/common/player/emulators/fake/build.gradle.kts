plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    js { nodejs() }

    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.player.emulators.fake"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.player.emulators.api)
                api(libs.kotlinx.coroutines.core)

                implementation(projects.cbox.common.repository.api)
            }
        }

        named("commonTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.sage.common.logging)
            }
        }
    }
}
