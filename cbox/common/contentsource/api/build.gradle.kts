plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    js { nodejs() }

    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.contentsource.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(libs.sage.common.coroutines)
            }
        }
    }
}
