plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
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
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.sage.common.logging)
                api(libs.okio)
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
