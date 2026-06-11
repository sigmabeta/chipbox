plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
}

kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.common.utils.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(libs.kotlinx.coroutines.core)
            }
        }

        // junrar is a pure-JVM RAR decoder (no Native/JS port). It backs the `unrar` actual that
        // unpacks RSN sound-chip sets (solid RAR archives of SPC files); JS gets a null stub.
        named("jvmSharedMain") {
            dependencies {
                implementation(chipbox.junrar)
            }
        }

        named("commonTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
