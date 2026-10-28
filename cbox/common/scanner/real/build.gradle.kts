plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
    alias(chipbox.plugins.kmp.test)
}

// Production scanner — pure Kotlin now that it talks to the library via the platform-neutral
// `LibrarySource` interface. Both Android (SAF impl in cbox/android/contentsource/file/real)
// and JVM (LocalFileContentSource in apps/jvm) drive this same code.
kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.scanner.real"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.scanner.api)
                api(projects.cbox.common.repository.api)
                api(projects.cbox.common.contentsource.api)
                api(projects.cbox.common.readers.api)
                implementation(projects.cbox.common.perf.api)
                implementation(projects.cbox.common.utils.api)
                // okio for the SHA-256 folder signature (multiplatform, replaces java MessageDigest).
                implementation(libs.okio)
                implementation(libs.sage.common.logging)
            }
        }

        named("commonTest") {
            dependencies {
                implementation(projects.cbox.common.contentsource.fake)
                implementation(projects.cbox.common.repository.fake)
            }
        }

        named("jvmTest") {
            dependencies {
                // The scanner-behaviour test drives the public Scanner over a stub LibrarySource;
                // FakeRepository is enough because the assertions only inspect emitted state.
                implementation(projects.cbox.common.repository.fake)
                implementation(libs.sage.common.logging)
            }
        }
    }
}
