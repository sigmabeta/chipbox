plugins {
    alias(libs.plugins.sage.kmp)
}

// Pure Kotlin JNI wrapper — builds for both the JVM and Android variants. The native `.so`
// is produced outside this module: Android packages it via the sibling `:twosf:native`
// companion (externalNativeBuild can't live in an AGP KMP library); the JVM target
// host-builds it into apps/jvm/libs. This single module replaces the old android/jvm twins.
kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.player.emulators.twosf.real"
    }

    sourceSets {
        named("jvmSharedMain") {
            dependencies {
                api(projects.cbox.common.player.common.api)
                api(projects.cbox.common.player.emulators.api)
                implementation(projects.cbox.common.repository.api)
            }
        }
    }
}
