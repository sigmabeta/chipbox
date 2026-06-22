plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
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
