plugins {
    alias(libs.plugins.sage.kmp)
}

// Production filesystem LibrarySource impls, one per platform: SAF DocumentsContract on Android
// (androidMain) and a plain java.io.File walker on the JVM/desktop (jvmMain). Both implement the
// common LibrarySource; each platform's wiring picks its own. Replaces the pre-KMP split (the
// android/contentsource/file/real module + duplicated LocalFileContentSource.kt twins in
// apps/jvm and apps/cli).
kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.common.contentsource.file.real"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.contentsource.api)
                implementation(libs.sage.common.logging)
            }
        }
    }
}
