plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    js { nodejs() }

    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.player.director.real"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.player.director.api)

                implementation(projects.cbox.common.player.common.api)
                implementation(projects.cbox.common.player.generator.api)
                implementation(projects.cbox.common.player.speaker.api)
                implementation(projects.cbox.common.repository.api)
                implementation(libs.sage.common.logging)
            }
        }

        named("commonTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.core)
                // No alias in libs.versions.toml yet; pin to the same coroutines version as
                // core so runTest / TestScope APIs line up with the production runtime.
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
            }
        }
    }
}
