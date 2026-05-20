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
                implementation(libs.sage.common.logging)
            }
        }
    }
}
