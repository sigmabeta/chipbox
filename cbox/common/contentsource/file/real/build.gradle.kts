plugins {
    alias(libs.plugins.sage.kmp)
}

// Production filesystem LibrarySource impl: an okio-backed path walker in commonMain, shared by
// every target (JVM/desktop, Android, CLI, server). All I/O goes through an injected okio
// FileSystem, so there's no java.io and no per-platform twin. Android dropped SAF/DocumentsContract
// in favour of MANAGE_EXTERNAL_STORAGE + raw paths, so it uses this same source. Replaces the
// pre-KMP split (the android/contentsource/file/real module + duplicated LocalFileContentSource.kt
// twins in apps/jvm and apps/cli).
kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.common.contentsource.file.real"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.contentsource.api)
                implementation(libs.sage.common.logging)
                implementation(libs.okio)
            }
        }

        named("commonTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.okio.fakefilesystem)
                // okio-fakefilesystem was built against pre-0.7 kotlinx-datetime — see
                // cbox/common/organizer/real for the same pin. Restricting to the test classpath
                // so the production runtime stays on whatever the rest of the build resolves.
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1") {
                    version { strictly("0.6.1") }
                }
            }
        }
    }
}
