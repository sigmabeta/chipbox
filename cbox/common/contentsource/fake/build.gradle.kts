plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
}

// Test-only LibrarySource implementation that lets tests drive the `locations` StateFlow and
// count add/remove calls without going through SAF (Android) or the filesystem (JVM).
kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.contentsource.fake"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.contentsource.api)

                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}
