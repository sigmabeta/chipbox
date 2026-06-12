plugins {
    alias(libs.plugins.sage.kmp)
}

// Production Speaker implementations, one per platform: AudioTrack on Android (androidMain) and
// javax.sound SourceDataLine on the JVM/desktop (jvmMain). Both subclass the common Speaker and
// each platform's DI picks its own — no expect/actual needed. Replaces the pre-KMP split
// (the android/player/speaker/real module + a loose SourceDataLineSpeaker.kt in apps/jvm).
kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.common.player.speaker.real"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.player.speaker.api)
                api(projects.cbox.common.player.buffer.api)
                implementation(projects.cbox.common.player.resampler.api)
                implementation(projects.cbox.common.settings.api)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.sage.common.logging)
            }
        }
    }
}
