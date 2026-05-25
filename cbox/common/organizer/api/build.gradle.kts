plugins {
    alias(libs.plugins.sage.kmp)
}

// Plan/result types for "organize library", all commonMain: [OrganizeResult], the Invalid-Folders
// bucket name, and [FolderMove] (which references okio.Path rather than java.io.File, so it stays
// multiplatform). The planner/executor that produces and applies these is in cbox/common/organizer/real.
kotlin {
    js { nodejs() }

    androidLibrary {
        namespace = "net.sigmabeta.chipbox.organizer.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(libs.okio)
            }
        }
    }
}
