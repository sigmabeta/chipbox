plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
}

// Test fake subclassing the Scanner abstract class with a no-op scan() body and a startCount
// counter — production Scanner.startScan() is final and launches scan() into its own scope, so
// counting scan() invocations is the only externally observable signal that startScan ran.
kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.scanner.fake"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.scanner.api)

                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}
