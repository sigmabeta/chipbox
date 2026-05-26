plugins {
    alias(libs.plugins.sage.kmp)
}

// LibraryOrganizer — pure planning + on-disk execution of the $destination/$platform/$game layout.
// All commonMain: it walks and moves files through an injected okio FileSystem (no java.io), and
// takes a StringProvider so platform folder names match what the apps render. The plan/result types
// it produces are in cbox/common/organizer/api. Usable by any app (Android / JVM / CLI), not just one.
kotlin {
    js { nodejs() }

    androidLibrary {
        namespace = "net.sigmabeta.chipbox.organizer.real"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.organizer.api)
                api(projects.cbox.common.models.api)
                implementation(libs.okio)
                implementation(libs.sage.common.ui.strings)
            }
        }

        named("commonTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.okio.fakefilesystem)
                // okio-fakefilesystem 3.9.1 was built against pre-0.7 kotlinx-datetime — see
                // cbox/common/coverart/real for the same pin. Restricting to the test classpath
                // so the production runtime stays on whatever the rest of the build resolves.
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1") {
                    version { strictly("0.6.1") }
                }
            }
        }
    }
}
