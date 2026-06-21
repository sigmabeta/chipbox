plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.playlists.fake"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.playlists.api)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}
