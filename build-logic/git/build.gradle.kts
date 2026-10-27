import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "net.sigmabeta.chipbox.buildlogic"

// Build identity is a leaf plugin: the modules that stamp their branch/commits are also the ones
// that would otherwise not touch build-logic at all, so it deliberately declares NO dependencies.
// Keeping it out of :convention means applying it does not drag paparazzi/metro/AGP onto the
// module's build script classpath (that classpath is set up on every build, configuration cache
// hit or not).
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

gradlePlugin {
    plugins {
        register("chipboxBuildIdentity") {
            id = "chipbox.build.identity"
            implementationClass = "ChipboxBuildIdentityPlugin"
        }
    }
}

dependencies {
    // The `.git` resolver is pure (a File + a read function), so its regression tests need no
    // Gradle test fixture — just kotlin-test/JUnit 4.
    testImplementation(kotlin("test-junit"))
}
