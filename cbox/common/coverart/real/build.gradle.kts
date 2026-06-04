plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
    alias(libs.plugins.kotlin.serialization)
}

// Implementations behind the cover-art domain (coverart/api). The logic is all commonMain — files
// via okio, time via kotlin.time.Clock, waits via coroutines, and the HTTP boundary behind the
// [CoverArtHttp] interface — so Android, the desktop JVM app and the CLI all share it (the js target
// keeps the metadata compile honest about purity). The one platform-bound piece, the OkHttp-backed
// [CoverArtHttp] impl, lives in jvmSharedMain (src/main/java), shared by the android + jvm targets.
kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.coverart.real"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.coverart.api)
                api(projects.cbox.common.models.api)
                implementation(libs.okio)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        named("commonTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.okio.fakefilesystem)
                // okio-fakefilesystem 3.9.1's FakeFileSystem.<init> references
                // kotlinx.datetime.Clock.System, which was removed in kotlinx-datetime 0.7.x
                // (Clock moved into kotlin.time in stdlib). Compose's transitive datetime is
                // 0.7.1; we strictly downgrade to 0.6.1 on the *test* classpath only so okio's
                // bytecode can resolve, without affecting main runtime.
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1") {
                    version { strictly("0.6.1") }
                }
            }
        }

        named("jvmSharedMain") {
            dependencies {
                implementation(libs.okhttp)
            }
        }
    }
}
