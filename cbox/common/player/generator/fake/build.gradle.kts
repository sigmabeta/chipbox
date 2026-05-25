plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    js { nodejs() }

    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.player.generator.fake"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.player.generator.api)

                implementation(projects.cbox.common.player.emulators.fake)
                implementation(projects.cbox.common.utils.api)
            }
        }
    }
}
