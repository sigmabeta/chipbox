plugins {
    alias(chipbox.plugins.feature.real)
    alias(chipbox.plugins.kmp.test)
}

// State / Action / VM / FolderLister (okio-backed) all live in commonMain — this module has no
// jvmSharedMain code. The Route is a commonMain `expect` with three actuals: androidMain (default
// = Environment.getExternalStorageDirectory), jvmMain (default = user.home), and an
// enforcement-only jsMain stub. ChipboxScreens.kt in appui's commonMain imports the `expect`.
// FileSystem is injected via Metro; the binding for `FileSystem.SYSTEM` lives in each app's DI
// module (AndroidAppModule, JvmModules).
kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.features.folderPicker.api)

                implementation(projects.cbox.common.ui.list.api)
                implementation(projects.cbox.common.appcomm.api)
                implementation(projects.cbox.common.strings.api)

                implementation(projects.cbox.common.contentsource.api)
                // "Add this folder" kicks off a scan and routes to the rescan-status screen.
                implementation(projects.cbox.common.scanner.api)
                implementation(projects.features.rescanStatus.api)

                // okio backs the FolderLister so the implementation stays in commonMain.
                implementation(libs.okio)
            }
        }

        named("commonTest") {
            dependencies {
                implementation(projects.cbox.common.contentsource.fake)
                implementation(projects.cbox.common.scanner.fake)
                // FakeFileSystem backs OkioFolderLister's tests — keeps them off real disk
                // while still exercising the okio code paths the production lister uses.
                implementation(libs.okio.fakefilesystem)
                // okio-fakefilesystem 3.9.1's FakeFileSystem.<init> references
                // kotlinx.datetime.Clock.System, which was removed in kotlinx-datetime 0.7.x
                // (Clock moved into kotlin.time in stdlib). Compose's transitive datetime is
                // 0.7.1; strict-downgrade to 0.6.1 on the *test* classpath only so okio's
                // bytecode resolves. Same workaround as cbox/common/coverart/real.
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1") {
                    version { strictly("0.6.1") }
                }
            }
        }
    }
}
