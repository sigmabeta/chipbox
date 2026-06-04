plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
    // sage.di's plugin uses `add("implementation", ...)` which is android/jvm-only — it errors
    // out on a KMP module that has no top-level `implementation` configuration. The metro
    // compiler plugin + the sage-common-di dep are added explicitly to commonMain below.
    alias(libs.plugins.metro)
}

// Mostly Android-specific image plumbing (Bitmap generation, Coil decoder/fetcher around
// platform Bitmap), with one common piece — `HatchetCoilLogger` — that bridges Coil's logging
// into [Hatchet]. The JS app's `buildWebImageLoader` reuses that logger, so the JS purity gate
// is enabled here; the Android-only Bitmap classes live in `src/androidMain/kotlin`, and the
// java.io.File-backed `FakeOtherImageFetcher` stays in `src/main/java` (which sage.kmp maps
// to `jvmSharedMain` — works on JVM + Android, not JS).
kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.images.api"
    }

    sourceSets {
        named("androidMain") {
            dependencies {
                implementation(libs.androidx.core.ktx)
            }
        }

        named("commonMain") {
            dependencies {
                api(libs.coil.kt.core)
                implementation(libs.sage.common.di)
                implementation(libs.sage.common.analytics)
                implementation(libs.sage.common.logging)
            }
        }

        named("jvmSharedMain") {
            dependencies {
                api(libs.coil.kt.compose)
                api(libs.coil.kt.okhttp)
                implementation(libs.sage.common.images)
            }
        }
    }
}
