plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.perf.api"
    }

    sourceSets {
        named("androidMain") {
            dependencies {
                // androidx.tracing over raw android.os.Trace: adds Trace.isEnabled() / lazy
                // string eval, works back to API 14, and is the artifact macrobenchmark and
                // Perfetto's app-tracing data source expect. Android-only — the JVM/desktop
                // actual is a no-op, so this stays out of jvmMain.
                implementation(libs.androidx.tracing.ktx)
            }
        }
    }
}
