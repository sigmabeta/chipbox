import org.gradle.caching.http.HttpBuildCache

val sageLocalProps = file("sage/local.properties")
if (!sageLocalProps.exists()) {
    val rootLocalProps = file("local.properties")
    if (rootLocalProps.exists()) {
        sageLocalProps.writeText(rootLocalProps.readText())
    }
}

includeBuild("build-logic")
includeBuild("sage/sage-build-logic")
includeBuild("sage")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Develocity Build Scans. The `sage` submodule pins its own (3.17.3) for standalone
    // builds, but in this composite build the root build owns the scan and sage's plugin
    // defers to it — so this must stay on the latest Gradle-9-compatible line, not match sage.
    id("com.gradle.develocity") version "4.4.2"
}

develocity {
    buildScan {
        // Free public Build Scan service (scans.gradle.com) — accept its terms non-interactively.
        termsOfUseUrl = "https://gradle.com/help/legal-terms-of-use"
        termsOfUseAgree = "yes"

        // Auto-publish on CI only — CircleCI (and most CI) export CI in the environment.
        // Locally nothing is uploaded unless you pass `--scan`, which overrides this predicate
        // and always publishes. Reading via providers keeps it configuration-cache safe.
        val isCi = providers.environmentVariable("CI").isPresent
        publishing.onlyIf { isCi }
        // Tag the CI-published scans so they're filterable apart from any local `--scan` runs.
        if (isCi) tag("CI")
    }
}

buildCache {
    // Local cache: fast within-build / same-machine reuse, kept on.
    local {
        isEnabled = true
    }
    // Remote: the self-hosted gradle/build-cache-node behind Caddy (TLS) at sebacloud.org.
    remote<HttpBuildCache> {
        setUrl("https://gradle.sebacloud.org/cache/")

        // Trust boundary — only CI pushes; everyone else reads anonymously. The node grants
        // anonymous read, so local dev needs no credentials. CircleCI exports CI in the env;
        // pushing additionally requires the write password, so a misconfigured CI can't half-push.
        // All reads go through providers to stay configuration-cache safe.
        val ciPassword = providers.environmentVariable("GRADLE_CACHE_PASSWORD")
        isPush = providers.environmentVariable("CI").isPresent && ciPassword.isPresent
        if (isPush) {
            credentials {
                username = providers.environmentVariable("GRADLE_CACHE_USER").orElse("ci").get()
                password = ciPassword.get()
            }
        }
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
        // Chipbox's own convention plugins live in a chipbox-owned catalog (not sage's), applied
        // as `alias(chipbox.plugins.<name>)`.
        create("chipbox") {
            from(files("$settingsDir/gradle/chipbox.versions.toml"))
        }
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "Chipbox"

// apps/js (and the Kotlin/JS variants its shared deps expose via sage.kmp.js) is gated behind
// -Pchipbox.js so ordinary Android/JVM builds and IDE syncs never configure Kotlin/JS. Include it
// only when the flag is set — an unconditional include made a no-flag sync fail to resolve its
// JS-only project deps (e.g. :cbox:common:appui:api exposes no JS variant without the flag).
// apps:server's web-bundle wiring is gated to match (see apps/server/build.gradle.kts).
if (providers.gradleProperty("chipbox.js").orNull.toBoolean()) {
    include(":apps:js")
}

include(
    // SAGE-aware modules
    ":apps:android",
    ":apps:jvm",
    ":apps:cli",
    ":apps:abrender",
    ":apps:abrender-core",
    ":apps:server",
    ":benchmark",

    ":cbox:common:appui:api",
    ":cbox:android:artworkprovider:api",
    ":cbox:android:contentsource:file:di",
    ":cbox:common:contentsource:file:real",
    ":cbox:android:coroutines:api",
    ":cbox:common:database:api",
    ":cbox:android:database:di",
    ":cbox:common:database:real",
    ":cbox:android:images:api",
    ":cbox:common:player-status:api",
    ":cbox:android:player:emulators:di",
    ":cbox:android:player:emulators:gba:native",
    ":cbox:common:player:emulators:gba:real",
    ":cbox:android:player:emulators:gme:native",
    ":cbox:common:player:emulators:gme:real",
    ":cbox:android:player:emulators:ncsf:native",
    ":cbox:common:player:emulators:ncsf:real",
    ":cbox:android:player:emulators:psf:native",
    ":cbox:common:player:emulators:psf:real",
    ":cbox:android:player:emulators:ssf:native",
    ":cbox:common:player:emulators:ssf:real",
    ":cbox:android:player:emulators:twosf:native",
    ":cbox:common:player:emulators:twosf:real",
    ":cbox:android:player:emulators:usf:native",
    ":cbox:common:player:emulators:usf:real",
    ":cbox:android:player:emulators:vgm:native",
    ":cbox:common:player:emulators:vgm:real",
    ":cbox:android:player:emulators:vgmstream:native",
    ":cbox:common:player:emulators:vgmstream:real",
    ":cbox:android:player:generator:di",
    ":cbox:common:player:generator:real",
    ":cbox:android:player:speaker:di",
    ":cbox:android:repository:di",
    ":cbox:common:repository:real",
    ":cbox:android:scanner:di",
    ":cbox:common:scanner:real",
    ":cbox:android:services:api",
    ":cbox:android:storage:api",
    ":cbox:common:ui:chrome:api",
    ":cbox:common:ui:components:api",
    ":cbox:common:ui:list:api",
    ":cbox:common:ui:freeform:api",
    ":cbox:common:uitest",
    ":cbox:android:ui:previews",
    ":cbox:android:ui:theme:api",

    ":cbox:common:appcomm:api",
    ":cbox:common:contentsource:api",
    ":cbox:common:contentsource:fake",
    ":cbox:common:coverart:api",
    ":cbox:common:coverart:real",
    ":cbox:common:database:fake",
    ":cbox:common:debug:api",
    ":cbox:common:debug:di",
    ":cbox:common:debug:fake",
    ":cbox:common:debug:real",
    ":cbox:common:debug-info:api",
    ":cbox:common:debug-info:di",
    ":cbox:common:debug-info:fake",
    ":cbox:common:debug-info:real",
    ":cbox:common:entities:api",
    ":cbox:common:models:api",
    ":cbox:common:organizer:api",
    ":cbox:common:organizer:real",
    ":cbox:common:perf:api",
    ":cbox:common:player:buffer:api",
    ":cbox:common:player:buffer:di",
    ":cbox:common:player:buffer:real",
    ":cbox:common:player:cache:api",
    ":cbox:common:player:cache:real",
    ":cbox:common:player:cache:fake",
    ":cbox:common:player:common:api",
    ":cbox:common:player:director:api",
    ":cbox:common:player:director:di",
    ":cbox:common:player:director:fake",
    ":cbox:common:player:director:real",
    ":cbox:common:crash:api",
    ":cbox:common:crash:real",
    ":cbox:common:player:emulators:api",
    ":cbox:common:player:emulators:di",
    ":cbox:common:player:emulators:fake",
    ":cbox:common:player:generator:api",
    ":cbox:common:player:generator:fake",
    ":cbox:common:player:persistence:api",
    ":cbox:common:player:persistence:di",
    ":cbox:common:player:persistence:fake",
    ":cbox:common:player:persistence:real",
    ":cbox:common:player:resampler:api",
    ":cbox:common:player:resampler:real",
    ":cbox:common:player:resampler:di",
    ":cbox:common:player:speaker:api",
    ":cbox:common:player:speaker:fake",
    ":cbox:common:player:speaker:real",
    ":cbox:common:readers:api",
    ":cbox:common:repository:api",
    ":cbox:common:repository:di",
    ":cbox:common:repository:fake",
    ":cbox:common:scanner:api",
    ":cbox:common:scanner:fake",
    ":cbox:common:settings:api",
    ":cbox:common:settings:di",
    ":cbox:common:settings:fake",
    ":cbox:common:settings:real",
    ":cbox:common:strings:api",
    ":cbox:common:strings:real",
    ":cbox:common:ui:fonts:api",
    ":cbox:common:ui:fonts:real",
    ":cbox:common:ui:theme:api",
    ":cbox:common:ui:vm:api",
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
    ":features:home:api",
    ":features:home:real",
    ":features:home:screenshot",
    ":features:artist-detail:api",
    ":features:artist-detail:real",
    ":features:artist-detail:screenshot",
    ":features:game-detail:api",
    ":features:game-detail:real",
    ":features:game-detail:screenshot",
    ":features:library:api",
    ":features:library:real",
    ":features:library:screenshot",
    ":features:folder-picker:api",
    ":features:folder-picker:real",
    ":features:folder-picker:screenshot",
    ":features:manage-library:api",
    ":features:manage-library:real",
    ":features:manage-library:screenshot",
    ":features:rescan-status:api",
    ":features:rescan-status:real",
    ":features:rescan-status:screenshot",
    ":features:now-playing:api",
    ":features:now-playing:real",
    ":features:now-playing:screenshot",
    ":features:playback-status:api",
    ":features:playback-status:real",
    ":features:error-log:api",
    ":features:error-log:real",
    ":features:crash-log:api",
    ":features:crash-log:real",
    ":features:component-library:api",
    ":features:component-library:real",
    ":features:search:api",
    ":features:search:real",
    ":features:search:screenshot",
    ":features:settings:api",
    ":features:settings:real",
)

// 2sf starts with a digit — map to a valid project name. :real moved to cbox/common; :native stays in cbox/android.
project(":cbox:android:player:emulators:twosf").projectDir = file("cbox/android/player/emulators/2sf")
project(":cbox:android:player:emulators:twosf:native").projectDir = file("cbox/android/player/emulators/2sf/native")
project(":cbox:common:player:emulators:twosf").projectDir = file("cbox/common/player/emulators/2sf")
project(":cbox:common:player:emulators:twosf:real").projectDir = file("cbox/common/player/emulators/2sf/real")
