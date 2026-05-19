plugins {
    alias(libs.plugins.sage.android)
}

// Android-only companion to `:twosfeal`. AGP's KMP library plugin has no externalNativeBuild
// DSL, so the CMake/NDK trigger for the `.so` lives here (no Kotlin). The Android app
// depends on this so the library is packaged into the APK; the JVM target host-builds the
// same cbox/native/2sf tree into apps/jvm/libs instead. ("native" is a Java keyword, so the
// namespace uses "nativelib".)
android {
    namespace = "net.sigmabeta.chipbox.player.emulators.twosf.nativelib"

    externalNativeBuild {
        cmake {
            path = rootProject.file("cbox/native/2sf/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
