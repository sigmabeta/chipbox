plugins {
    alias(libs.plugins.sage.kmp)
    id("chipbox.kmp.test")
}

kotlin {
    js { nodejs() }

    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.player.emulators.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.models.api)
                api(projects.cbox.common.player.common.api)
                api(libs.kotlinx.coroutines.core)
                api(libs.sage.common.logging)
            }
        }
    }
}
