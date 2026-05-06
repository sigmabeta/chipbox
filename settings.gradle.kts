val sageLocalProps = file("sage/local.properties")
if (!sageLocalProps.exists()) {
    val rootLocalProps = file("local.properties")
    if (rootLocalProps.exists()) {
        sageLocalProps.writeText(rootLocalProps.readText())
    }
}

includeBuild("sage/sage-build-logic")
includeBuild("sage")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("$settingsDir/sage/gradle/libs.versions.toml"))
        }
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "Chipbox"

include(
    // SAGE-aware modules
    ":app",

    ":features:welcome",

    ":cbox:android:activities",
    ":cbox:android:colors",
    ":cbox:android:components",
    ":cbox:android:database",
    ":cbox:android:database:di",
    ":cbox:android:drawables",
    ":cbox:android:image-loading",
    ":cbox:android:strings",
    ":cbox:android:styles",
    ":cbox:android:contentsource:file",
    ":cbox:android:scanner:real",
    ":cbox:android:navigation",
    ":cbox:android:services",
    ":cbox:android:scanner:real:di",
    ":cbox:android:contentsource:file:di",
    ":cbox:android:repository:database",
    ":cbox:android:repository:database:di",
    ":cbox:android:repository:mock",
    ":cbox:android:repository:mock:di",
    ":cbox:android:scanner:mock",
    ":cbox:android:scanner:mock:di",

    ":cbox:common:contentsource",
    ":cbox:common:entities",
    ":cbox:common:models",
    ":cbox:common:readers",
    ":cbox:common:repository",
    ":cbox:common:repository:memory",
    ":cbox:common:repository:memory:di",
    ":cbox:common:scanner",
    ":cbox:common:utils",

    // Legacy Modules
    ":cbox:common:player:buffer",
    ":cbox:common:player:buffer:di",
    ":cbox:common:player:buffer:real",
    ":cbox:common:player:buffer:real:di",
    ":cbox:common:player:common",
    ":cbox:common:player:director",
    ":cbox:android:player:director:di",
    ":cbox:common:player:director:real",
    ":cbox:android:player:director:real:di",
    ":cbox:common:player:emulators",
    ":cbox:android:player:emulators:di",
    ":cbox:android:player:emulators:twosf",
    ":cbox:android:player:emulators:twosf:di",
    ":cbox:common:player:emulators:fake",
    ":cbox:common:player:emulators:fake:di",
    ":cbox:android:player:emulators:gba",
    ":cbox:android:player:emulators:gba:di",
    ":cbox:android:player:emulators:gme",
    ":cbox:android:player:emulators:gme:di",
    ":cbox:android:player:emulators:psf",
    ":cbox:android:player:emulators:psf:di",
    ":cbox:android:player:emulators:ssf",
    ":cbox:android:player:emulators:ssf:di",
    ":cbox:common:player:generator",
    ":cbox:android:player:generator:di",
    ":cbox:common:player:generator:fake",
    ":cbox:android:player:generator:fake:di",
    ":cbox:android:player:generator:real",
    ":cbox:android:player:generator:real:di",
    ":cbox:common:player:speaker",
    ":cbox:android:player:speaker:di",
    ":cbox:common:player:speaker:file",
    ":cbox:android:player:speaker:file:di",
    ":cbox:android:player:speaker:real",
    ":cbox:android:player:speaker:real:di",
    ":cbox:common:player:speaker:text",
    ":cbox:android:player:speaker:text:di",
    ":cbox:common:player:status",
    ":cbox:common:player:status:di",
    ":cbox:common:player:status:real",
    ":cbox:common:player:status:real:di",

    ":features:artists",
    ":features:artist-detail",
    ":features:games",
    ":features:game-detail",
)

// 2sf starts with a digit — map to a valid project name
project(":cbox:android:player:emulators:twosf").projectDir = file("cbox/android/player/emulators/2sf")
project(":cbox:android:player:emulators:twosf:di").projectDir = file("cbox/android/player/emulators/2sf/di")
