plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.player.buffer.api"
    }

    sourceSets {
        named("jvmSharedMain") {
            dependencies {
                api(libs.kotlinx.coroutines.core)
            }
        }
    }
}
