plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.kotlin.serialization)
    alias(chipbox.plugins.kmp.test)
}

kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.common.crash.real"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.crash.api)

                // RealCrashReporter lives in jvmSharedMain (java.lang.Thread / java.io.File), so
                // these land there transitively through commonMain.
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.sage.common.appinfo)
                implementation(libs.sage.common.logging)
            }
        }

        named("commonTest") {
            dependencies {
                // RealCrashReporterTest decodes the written file back into a CrashReport.
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.sage.common.appinfo)
                implementation(libs.sage.common.logging)
            }
        }
    }
}
