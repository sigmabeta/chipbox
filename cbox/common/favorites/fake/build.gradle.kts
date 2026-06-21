plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.favorites.fake"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.favorites.api)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}
