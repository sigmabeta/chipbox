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
    ":core:player:director",
    ":core:player:director:di",
    ":core:player:director:real",
    ":core:player:director:real:di",
    ":core:player:emulators",
    ":core:player:emulators:di",
    ":core:player:emulators:twosf",
    ":core:player:emulators:twosf:di",
    ":core:player:emulators:fake",
    ":core:player:emulators:fake:di",
    ":core:player:emulators:gba",
    ":core:player:emulators:gba:di",
    ":core:player:emulators:gme",
    ":core:player:emulators:gme:di",
    ":core:player:emulators:psf",
    ":core:player:emulators:psf:di",
    ":core:player:emulators:ssf",
    ":core:player:emulators:ssf:di",
    ":core:player:generator",
    ":core:player:generator:di",
    ":core:player:generator:fake",
    ":core:player:generator:fake:di",
    ":core:player:generator:real",
    ":core:player:generator:real:di",
    ":core:player:speaker",
    ":core:player:speaker:di",
    ":core:player:speaker:file",
    ":core:player:speaker:file:di",
    ":core:player:speaker:real",
    ":core:player:speaker:real:di",
    ":core:player:speaker:text",
    ":core:player:speaker:text:di",
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
project(":core:player:emulators:twosf").projectDir = file("core/player/emulators/2sf")
project(":core:player:emulators:twosf:di").projectDir = file("core/player/emulators/2sf/di")
