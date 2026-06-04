plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
    id("chipbox.kmp.test")
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.player.cache.real"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.player.cache.api)
                api(projects.cbox.common.player.emulators.api)

                implementation(projects.cbox.common.contentsource.api)
                implementation(projects.cbox.common.player.common.api)
                implementation(projects.cbox.common.utils.api)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.sage.common.logging)
                api(libs.okio)
            }
        }

        named("jvmSharedTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.sage.common.logging)
                implementation(libs.okio.fakefilesystem)
                // CachingPcmSourceTest needs runTest + TestScope. CachingPcmSource holds
                // concurrent read+write handles on the same .pcm.tmp, which okio's FakeFileSystem
                // refuses ("file is already open for writing"); the test therefore runs against
                // FileSystem.SYSTEM in jvmSharedTest, alongside the existing PcmCacheFile /
                // PcmCacheJanitor JVM-only tests.
                implementation(projects.cbox.common.player.cache.fake)
            }
        }
    }
}
