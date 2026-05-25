// The chipbox convention plugins layer on the SAGE base plugins (sage.kmp, sage.di, sage.compose.*),
// which they apply by id — and a plugin applied programmatically resolves against the DEFINING
// build's classpath, so this build must include sage-build-logic and depend on its convention.
includeBuild("../sage/sage-build-logic")

dependencyResolutionManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            // The shared SAGE catalog also backs chipbox's own modules; reuse it here so the
            // chipbox convention plugins resolve the same plugin/library coordinates.
            from(files("../sage/gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "chipbox-build-logic"
include(":convention")
