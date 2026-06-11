plugins {
    alias(libs.plugins.sage.jvm)
    application
}

application {
    mainClass.set("net.sigmabeta.chipbox.abrender.MainKt")
    applicationName = "chipbox-abrender"
}

// The A/B render harness drives EVERY native emulator directly (USF, PSF, SSF, GME, ...), so unlike
// the CLI — which only needs libvgmstream.so for scan-time subsong probing — it needs the whole set
// of `.so` files on java.library.path. Rather than duplicate apps/jvm's host-CMake machinery, reuse
// the libs that module already builds: depend on its aggregate `chipboxHostNativeLibs` task and
// point java.library.path at its output dir (build/jvm-native/libs).
val jvmNativeLibsDir = project(":apps:jvm").layout.buildDirectory.dir("jvm-native/libs").get().asFile
val buildAllNativeLibs = ":apps:jvm:chipboxHostNativeLibs"

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    dependsOn(buildAllNativeLibs)
    systemProperty("java.library.path", jvmNativeLibsDir.absolutePath)
}

// Bundle every emulator .so into `lib/native/` inside the distribution and inject
// `-Djava.library.path=$APP_HOME/lib/native` into the start scripts — same approach as apps/jvm and
// apps/cli, so the installed `chipbox-abrender` finds the libs at launch.
distributions {
    named("main") {
        contents {
            from(jvmNativeLibsDir) {
                include("*.so")
                into("lib/native")
            }
        }
    }
}
listOf("installDist", "distZip", "distTar").forEach { taskName ->
    tasks.named<AbstractCopyTask>(taskName) {
        dependsOn(buildAllNativeLibs)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}
tasks.named<CreateStartScripts>("startScripts") {
    doLast {
        unixScript.writeText(
            unixScript.readText().replace(
                "exec \"\$JAVACMD\" \"\$@\"",
                "exec \"\$JAVACMD\" \"-Djava.library.path=\$APP_HOME/lib/native\" \"\$@\"",
            )
        )
        windowsScript.writeText(
            windowsScript.readText().replace(
                "-classpath \"%CLASSPATH%\"",
                "-D\"java.library.path=%APP_HOME%\\lib\\native\" -classpath \"%CLASSPATH%\"",
            )
        )
    }
}

// The harness is headless and never renders Compose, but models.api (transitively pulled by
// repository/emulators) declares Compose UI deps via the sage.compose.kmp convention plugin, which
// drags the desktop Compose runtime + skiko (~13 MB) onto the runtime classpath. None of it is
// reachable here, and the duplicate skiko artifacts otherwise break the dist Copy — exclude them at
// the configuration level, mirroring apps/cli.
configurations.runtimeClasspath {
    exclude(group = "org.jetbrains.compose.foundation")
    exclude(group = "org.jetbrains.compose.material")
    exclude(group = "org.jetbrains.compose.material3")
    exclude(group = "org.jetbrains.compose.ui")
    exclude(group = "org.jetbrains.compose.animation")
    exclude(group = "org.jetbrains.compose.components", module = "components-resources")
    exclude(group = "org.jetbrains.skiko")
}

dependencies {
    // Render loop, metrics, WAV writer, and corpus walker — plus, transitively, every emulator JNI
    // wrapper (object XxxEmulator → System.loadLibrary) and EbuR128. This module keeps only the CLI
    // shell (Main, DiffCommand); the engine it drives lives in :apps:abrender-core so the same code
    // also renders on-device. The JVM variant of the core pulls the JVM JNI wrappers; the .so files
    // come from :apps:jvm's host build via java.library.path (configured above).
    implementation(projects.apps.abrenderCore)
}
