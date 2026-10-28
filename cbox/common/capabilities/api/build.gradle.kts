plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
    alias(chipbox.plugins.kmp.test)
}

kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.common.capabilities.api"
    }

    sourceSets {
        named("commonTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
