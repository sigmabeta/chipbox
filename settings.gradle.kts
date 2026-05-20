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
    ":apps:android",
    ":apps:jvm",
    ":benchmark",

    ":cbox:android:appui:api",
    ":cbox:android:artworkprovider:api",
    ":cbox:android:colors:api",
    ":cbox:android:contentsource:file:all",
    ":cbox:android:contentsource:file:api",
    ":cbox:android:contentsource:file:di",
    ":cbox:android:contentsource:file:real",
    ":cbox:android:coroutines:api",
    ":cbox:android:database:all",
    ":cbox:android:database:api",
    ":cbox:android:database:di",
    ":cbox:android:database:real",
    ":cbox:android:image-loading:api",
    ":cbox:android:images:api",
    ":cbox:android:player-status:api",
    ":cbox:android:player:emulators:di",
    ":cbox:android:player:emulators:gba:all",
    ":cbox:android:player:emulators:gba:api",
    ":cbox:android:player:emulators:gba:di",
    ":cbox:android:player:emulators:gba:native",
    ":cbox:android:player:emulators:gba:real",
    ":cbox:android:player:emulators:gme:all",
    ":cbox:android:player:emulators:gme:api",
    ":cbox:android:player:emulators:gme:di",
    ":cbox:android:player:emulators:gme:native",
    ":cbox:android:player:emulators:gme:real",
    ":cbox:android:player:emulators:psf:all",
    ":cbox:android:player:emulators:psf:api",
    ":cbox:android:player:emulators:psf:di",
    ":cbox:android:player:emulators:psf:native",
    ":cbox:android:player:emulators:psf:real",
    ":cbox:android:player:emulators:ssf:all",
    ":cbox:android:player:emulators:ssf:api",
    ":cbox:android:player:emulators:ssf:di",
    ":cbox:android:player:emulators:ssf:native",
    ":cbox:android:player:emulators:ssf:real",
    ":cbox:android:player:emulators:twosf:all",
    ":cbox:android:player:emulators:twosf:api",
    ":cbox:android:player:emulators:twosf:di",
    ":cbox:android:player:emulators:twosf:native",
    ":cbox:android:player:emulators:twosf:real",
    ":cbox:android:player:emulators:usf:all",
    ":cbox:android:player:emulators:usf:api",
    ":cbox:android:player:emulators:usf:di",
    ":cbox:android:player:emulators:usf:native",
    ":cbox:android:player:emulators:usf:real",
    ":cbox:android:player:emulators:vgm:all",
    ":cbox:android:player:emulators:vgm:api",
    ":cbox:android:player:emulators:vgm:di",
    ":cbox:android:player:emulators:vgm:native",
    ":cbox:android:player:emulators:vgm:real",
    ":cbox:android:player:generator:di",
    ":cbox:android:player:generator:real",
    ":cbox:android:player:speaker:di",
    ":cbox:android:player:speaker:real",
    ":cbox:android:repository:all",
    ":cbox:android:repository:api",
    ":cbox:android:repository:di",
    ":cbox:android:repository:fake",
    ":cbox:android:repository:real",
    ":cbox:android:scanner:all",
    ":cbox:android:scanner:api",
    ":cbox:android:scanner:di",
    ":cbox:android:scanner:fake",
    ":cbox:android:scanner:real",
    ":cbox:android:services:api",
    ":cbox:android:storage:api",
    ":cbox:android:strings:api",
    ":cbox:android:ui:chrome:api",
    ":cbox:android:ui:components:api",
    ":cbox:android:ui:fonts:api",
    ":cbox:android:ui:list:api",
    ":cbox:android:ui:freeform:api",
    ":cbox:android:ui:previews",
    ":cbox:android:ui:theme:api",

    ":cbox:common:appcomm:api",
    ":cbox:common:contentsource:api",
    ":cbox:common:debug:api",
    ":cbox:common:debug:di",
    ":cbox:common:debug:real",
    ":cbox:common:debug-info:api",
    ":cbox:common:debug-info:di",
    ":cbox:common:debug-info:real",
    ":cbox:common:entities:api",
    ":cbox:common:models:api",
    ":cbox:common:player:buffer:all",
    ":cbox:common:player:buffer:api",
    ":cbox:common:player:buffer:di",
    ":cbox:common:player:buffer:real",
    ":cbox:common:player:cache:all",
    ":cbox:common:player:cache:api",
    ":cbox:common:player:cache:real",
    ":cbox:common:player:common:api",
    ":cbox:common:player:director:all",
    ":cbox:common:player:director:api",
    ":cbox:common:player:director:di",
    ":cbox:common:player:director:real",
    ":cbox:common:player:emulators:all",
    ":cbox:common:player:emulators:api",
    ":cbox:common:player:emulators:di",
    ":cbox:common:player:emulators:fake",
    ":cbox:common:player:generator:all",
    ":cbox:common:player:generator:api",
    ":cbox:common:player:generator:fake",
    ":cbox:common:player:speaker:all",
    ":cbox:common:player:speaker:api",
    ":cbox:common:player:speaker:fake",
    ":cbox:common:readers:api",
    ":cbox:common:repository:all",
    ":cbox:common:repository:api",
    ":cbox:common:repository:di",
    ":cbox:common:repository:fake",
    ":cbox:common:scanner:api",
    ":cbox:common:settings:api",
    ":cbox:common:settings:di",
    ":cbox:common:settings:real",
    ":cbox:common:strings:api",
    ":cbox:common:ui:theme:api",
    ":cbox:common:utils:api",

    ":features:browse-all-tracks:api",
    ":features:browse-all-tracks:real",
    ":features:browse-all-tracks:screenshot",
    ":features:browse-by-artist:api",
    ":features:browse-by-artist:real",
    ":features:browse-by-artist:screenshot",
    ":features:browse-by-game:api",
    ":features:browse-by-game:real",
    ":features:browse-by-game:screenshot",
    ":features:browse-by-platform:api",
    ":features:browse-by-platform:real",
    ":features:browse-by-platform:screenshot",
    ":features:games-for-platform:api",
    ":features:games-for-platform:real",
    ":features:games-for-platform:screenshot",
    ":features:artist-detail:api",
    ":features:artist-detail:real",
    ":features:artist-detail:screenshot",
    ":features:game-detail:api",
    ":features:game-detail:real",
    ":features:game-detail:screenshot",
    ":features:library:api",
    ":features:library:real",
    ":features:library:screenshot",
    ":features:now-playing:api",
    ":features:now-playing:real",
    ":features:playback-status:api",
    ":features:playback-status:fake",
    ":features:playback-status:real",
    ":features:search:api",
    ":features:search:real",
    ":features:search:screenshot",
    ":features:settings:api",
    ":features:settings:real",
)

// 2sf starts with a digit — map to a valid project name
project(":cbox:android:player:emulators:twosf").projectDir = file("cbox/android/player/emulators/2sf")
project(":cbox:android:player:emulators:twosf:all").projectDir = file("cbox/android/player/emulators/2sf/all")
project(":cbox:android:player:emulators:twosf:api").projectDir = file("cbox/android/player/emulators/2sf/api")
project(":cbox:android:player:emulators:twosf:di").projectDir = file("cbox/android/player/emulators/2sf/di")
project(":cbox:android:player:emulators:twosf:real").projectDir = file("cbox/android/player/emulators/2sf/real")
project(":cbox:android:player:emulators:twosf:native").projectDir = file("cbox/android/player/emulators/2sf/native")
