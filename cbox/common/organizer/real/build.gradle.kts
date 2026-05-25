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
    }
}
