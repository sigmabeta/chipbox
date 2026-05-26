plugins {
    alias(libs.plugins.sage.kmp)
}

// Test-only LibrarySource implementation that lets tests drive the `locations` StateFlow and
// count add/remove calls without going through SAF (Android) or the filesystem (JVM).
kotlin {
    js { nodejs() }

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
