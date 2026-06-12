plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.metro)
    alias(libs.plugins.detekt)
    // Derives versionCode/versionName from the latest git tag for release builds (see the
    // appVersioning {} block below). Same plugin VGLS uses; coords live in sage's catalog.
    alias(libs.plugins.git.version)
}

// The app module configures AGP directly rather than via a sage convention, so detekt isn't
// applied for free here the way it is in every library module. Wire it up by hand with the
// same root config + per-module baseline the sage convention's `configureDetekt()` uses, so
// `./gradlew detekt` covers the app's own sources too.
detekt {
    config.setFrom("$rootDir/detekt-config.yml")
    baseline = file("detekt-baseline.xml")
}

// Metro's `interop.includeDagger()` keeps the compiler plugin recognising the existing
// Dagger-shaped `@Inject` / `@Provides` / `@Module` / `@Binds` annotations on the
// `@ContributesTo(AppScope::class)` modules across the compile classpath. The Hilt plugin
// and KSP step are gone (Milestone 6 — see docs/architecture/sage-integration.md); the Dagger annotations
// stay because rewriting them as Metro-native (`@SingleIn(AppScope::class)`, etc.) buys
// nothing functional and would churn 30+ module files.
metro {
    interop {
        includeDagger()
    }
}

fun gitBranch(): String = providers.exec {
    commandLine("git", "rev-parse", "--abbrev-ref", "HEAD")
}.standardOutput.asText.get().trim().ifEmpty { "unknown" }

android {
    namespace = "net.sigmabeta.chipbox"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.sigmabeta.chipbox"
        minSdk = 26
        targetSdk = 36
        // Fallback values for debug builds. Release builds have these overridden from the
        // latest git tag by the appVersioning {} block (releaseBuildOnly = true).
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("long", "BUILD_TIME_MS", "${System.currentTimeMillis()}L")
        buildConfigField("String", "BUILD_BRANCH", "\"${gitBranch()}\"")
    }

    signingConfigs {
        create("release") {
            // Set these in CircleCI project settings → Environment Variables.
            // chipbox.jks lives at the repo root and is committed to the repo.
            val ksAlias = System.getenv("CHIPBOX_KEY_ALIAS")
            val ksPass = System.getenv("CHIPBOX_KEYSTORE_PASSWORD")
            val keyPass = System.getenv("CHIPBOX_KEY_PASSWORD")
            if (ksAlias != null && ksPass != null && keyPass != null) {
                storeFile = rootProject.file("chipbox.jks")
                storePassword = ksPass
                keyAlias = ksAlias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            // Debug installs as a separate app (net.sigmabeta.chipbox.debug) so it can sit
            // alongside a release build. The artwork ContentProvider's authority is derived from
            // the applicationId at runtime (ArtworkUris, set in ChipboxApplication.attachBaseContext)
            // and declared with ${applicationId} in the manifest, so the two installs don't collide
            // on a duplicate provider authority.
            applicationIdSuffix = ".debug"
        }
        getByName("release") {
            isMinifyEnabled = false
            // Release-signed with chipbox.jks when the CHIPBOX_* env vars are
            // present (CI); falls back to debug signing for local builds.
            signingConfig = if (System.getenv("CHIPBOX_KEY_ALIAS") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        create("benchmark") {
            initWith(getByName("release"))
            isDebuggable = false
            isProfileable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(projects.cbox.common.appui.api)
    implementation(projects.cbox.common.crash.real)
    implementation(projects.cbox.common.strings.real)
    implementation(projects.cbox.android.artworkprovider.api)
    implementation(projects.cbox.common.database.real)
    implementation(projects.cbox.android.database.di)
    implementation(projects.cbox.android.services.api)
    implementation(projects.cbox.android.contentsource.file.di)
    implementation(projects.cbox.common.player.buffer.di)
    implementation(projects.cbox.common.player.director.di)
    implementation(projects.cbox.common.player.persistence.di)
    implementation(projects.cbox.android.player.emulators.di)
    implementation(projects.cbox.common.player.emulators.di)
    implementation(projects.cbox.android.player.generator.di)
    implementation(projects.cbox.android.player.speaker.di)
    implementation(projects.cbox.common.player.resampler.di)
    implementation(projects.cbox.android.repository.di)
    implementation(projects.cbox.common.repository.di)
    implementation(projects.cbox.android.scanner.di)
    implementation(projects.cbox.common.ui.components.api)
    implementation(projects.cbox.android.coroutines.api)
    implementation(projects.cbox.android.storage.api)
    implementation(projects.cbox.common.debug.di)
    implementation(projects.cbox.common.debugInfo.di)
    implementation(libs.sage.common.di)
    implementation(projects.cbox.common.settings.di)
    implementation(projects.features.settings.api)
    implementation(projects.features.settings.real)

    implementation(libs.sage.common.appinfo)

    implementation(libs.sage.common.list)
    implementation(libs.sage.common.appcomm)
    implementation(libs.sage.common.analytics)
    implementation(libs.sage.common.logging)
    implementation(libs.sage.common.coroutines)
    implementation(libs.sage.common.ui.components)
    implementation(libs.sage.common.ui.strings)

    implementation(libs.sage.android.coroutines)
    implementation(libs.sage.android.logging)
    implementation(libs.sage.common.ui.perfCompose)
    implementation(libs.sage.common.ui.listScreens)
    implementation(libs.sage.android.ui.themes)

    implementation(libs.androidx.core.splash)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.compose.runtime.tracing)

    implementation(libs.metrox.viewmodel)
    implementation(libs.metrox.viewmodel.compose)
}

// Version code / name derived from the latest git tag, structured the same way VGLS does it:
// the human-readable name is "<tag>.<commits-since-tag>", and the integer code packs the version
// components into fixed place-value slots so it strictly increases across builds and branches.
// Release builds only — debug keeps the defaultConfig fallbacks above so local installs don't need
// git history / tags.
appVersioning {
    releaseBuildOnly.set(true)

    overrideVersionCode { gitTag, _, _ ->
        val rawTag = gitTag.rawTagName
        println("Generating version code. Git tag: $rawTag")

        // Tags look like "2.1.1", "3.0", or "3.0-alpha01": "<numbers>[-<prerelease label>]".
        // Parse tolerantly so a non-strict tag never crashes a release build.
        val (numberPart, prereleaseLabel) = rawTag.split('-', limit = 2)
            .let { it[0] to it.getOrNull(1) }
        val segments = numberPart.split('.')
        val major = segments.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = segments.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = segments.getOrNull(2)?.toIntOrNull() ?: 0

        // A final release (no "-alphaNN" suffix) outranks every prerelease of the same x.y.z, so it
        // takes the top prerelease slot; an "alphaNN"/"betaNN" tag uses its own number below that.
        val prerelease = if (prereleaseLabel == null) {
            Versions.FINAL_RELEASE_PRERELEASE
        } else {
            prereleaseLabel.filter(Char::isDigit).toIntOrNull() ?: 0
        }

        val commits = gitTag.commitsSinceLatestTag

        // Only the mobile APK ships today; widen to a when() if another platform gets a store build.
        val platType = 1

        val branch = when (System.getenv("CIRCLE_BRANCH")) {
            "release" -> 9
            "beta" -> 8
            else -> 7
        }

        Versions.verifyRequirements("Major", major, Versions.MAX_MAJOR_VERSIONS)
        Versions.verifyRequirements("Minor", minor, Versions.MAX_MINOR_VERSIONS)
        Versions.verifyRequirements("Patch", patch, Versions.MAX_PATCH_VERSIONS)
        Versions.verifyRequirements("Prerelease", prerelease, Versions.MAX_PRERELEASES)
        Versions.verifyRequirements("Commit count", commits, Versions.MAX_COMMITS)
        Versions.verifyRequirements("Platform type", platType, Versions.MAX_PLAT_TYPES)
        Versions.verifyRequirements("Branch", branch, Versions.MAX_BRANCHES)

        major * Versions.MAJOR +
            minor * Versions.MINOR +
            patch * Versions.PATCH +
            prerelease * Versions.PRERELEASE +
            commits * Versions.COMMIT +
            platType * Versions.PLAT_TYPE +
            branch * Versions.BRANCH
    }

    overrideVersionName { gitTag, _, _ ->
        val commits = gitTag.commitsSinceLatestTag
        "${gitTag.rawTagName}.$commits"
    }
}

object Versions {
    // Per-tier capacities. Each component must stay below its MAX_* or verifyRequirements fails the
    // build loudly — that's the guard that keeps the place-value packing below non-overlapping.
    const val MAX_MAJOR_VERSIONS = 21 // major * MAJOR must keep the code under Int.MAX_VALUE
    const val MAX_MINOR_VERSIONS = 10
    const val MAX_PATCH_VERSIONS = 10
    const val MAX_PRERELEASES = 100
    const val MAX_COMMITS = 100
    const val MAX_PLAT_TYPES = 10
    const val MAX_BRANCHES = 10

    // Place-value weights, least-significant tier first. Each weight is the next-lower tier's weight
    // times that lower tier's capacity, so tiers never bleed into each other while every component
    // stays under its MAX_*. Largest possible code (major=20, everything else maxed) stays under
    // Int.MAX_VALUE (2_147_483_647), which is the Android versionCode ceiling.
    const val BRANCH = 1
    const val PLAT_TYPE = BRANCH * MAX_BRANCHES // 10
    const val COMMIT = PLAT_TYPE * MAX_PLAT_TYPES // 100
    const val PRERELEASE = COMMIT * MAX_COMMITS // 10_000
    const val PATCH = PRERELEASE * MAX_PRERELEASES // 1_000_000
    const val MINOR = PATCH * MAX_PATCH_VERSIONS // 10_000_000
    const val MAJOR = MINOR * MAX_MINOR_VERSIONS // 100_000_000

    // The slot a final release takes in the prerelease tier — above any real "alphaNN" number.
    const val FINAL_RELEASE_PRERELEASE = MAX_PRERELEASES - 1 // 99

    fun verifyRequirements(type: String, actual: Int, max: Int) {
        require(actual in 0 until max) {
            "$type value $actual is outside the allowed range [0, $max)."
        }

        println("Version component $type: $actual")
    }
}
