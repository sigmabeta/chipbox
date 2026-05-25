plugins {
    alias(libs.plugins.sage.kmp)
}

// Production scanner — pure Kotlin now that it talks to the library via the platform-neutral
// `LibrarySource` interface. Both Android (SAF impl in cbox/android/contentsource/file/real)
// and JVM (LocalFileContentSource in apps/jvm) drive this same code.
kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.scanner.real"
    }

    sourceSets {
        named("jvmSharedMain") {
            dependencies {
                api(projects.cbox.common.scanner.api)
                api(projects.cbox.common.repository.api)
                api(projects.cbox.common.contentsource.api)
                api(projects.cbox.common.readers.api)
                implementation(projects.cbox.common.perf.api)
                // vgmstream metadata probe (native): enumerates subsongs + exact lengths at scan
                // time. The shared .so is packaged by the app via emulators:di → vgmstream:native.
                implementation(projects.cbox.common.player.emulators.vgmstream.real)
                implementation(libs.sage.common.logging)
            }
        }
    }
}
