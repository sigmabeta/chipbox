plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    js { nodejs() }

    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.player.cache.real"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.player.cache.api)
                api(projects.cbox.common.player.emulators.api)

                implementation(projects.cbox.common.contentsource.api)
                implementation(projects.cbox.common.player.common.api)
                implementation(projects.cbox.common.utils.api)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.sage.common.logging)
                api(libs.okio)
            }
        }

        named("commonTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.sage.common.logging)
                // No alias in libs.versions.toml yet; pin to the same coroutines version as core
                // so runTest / TestScope APIs line up exactly with the production runtime.
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
            }
        }

        named("jvmSharedTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.sage.common.logging)
                implementation(libs.okio.fakefilesystem)
            }
        }
    }
}
