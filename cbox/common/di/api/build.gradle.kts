plugins {
    alias(libs.plugins.sage.kmp)
}

// Holds DI marker types used by Metro graphs (AppScope, etc.). Pure-Kotlin types only —
// no Metro runtime dep here; consumers apply the Metro plugin and reference these by FQCN
// in their @DependencyGraph / @ContributesTo / @SingleIn annotations.
kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.di.api"
    }
}
