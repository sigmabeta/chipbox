plugins {
    alias(libs.plugins.sage.kmp)
}

// Production scanner — pure Kotlin now that it talks to the library via the platform-neutral
// `LibrarySource` interface. Both Android (SAF impl in cbox/android/contentsource/file/real)
// and JVM (LocalFileContentSource in apps/jvm) drive this same code.
kotlin {
    js { nodejs() }

    androidLibrary {
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
                // VgmstreamProber interface (the native subsong probe). The native impl lives in
                // vgmstream/real and is injected via DI, keeping this module platform-neutral / JS-able.
                implementation(projects.cbox.common.player.emulators.api)
                // okio for the SHA-256 folder signature (multiplatform, replaces java MessageDigest).
                implementation(libs.okio)
                implementation(libs.sage.common.logging)
            }
        }
    }
}
