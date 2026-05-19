plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.player.buffer.all"
    }

    sourceSets {
        named("jvmSharedMain") {
            dependencies {
                api(projects.cbox.common.player.buffer.api)
                api(projects.cbox.common.player.buffer.real)
            }
        }
    }
}
