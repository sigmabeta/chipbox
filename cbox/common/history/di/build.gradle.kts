plugins {
    alias(libs.plugins.sage.jvm)
    alias(libs.plugins.sage.di)
}

// Platform-agnostic history wiring: builds the repository from the HistoryDatabase DAOs and the
// recorder from the director. The HistoryDatabase itself is provided per-platform (Android:
// cbox/android/history/di; JVM: apps/jvm JvmHistoryModule), mirroring how ChipboxDatabase is
// provided. Shared here (like player/persistence/di) so both graphs reuse the same providers.
dependencies {
    api(projects.cbox.common.history.api)
    api(projects.cbox.common.history.real)

    // The debug switch picks Real vs Fake at graph build.
    implementation(projects.cbox.common.history.fake)
    implementation(projects.cbox.common.debug.api)
    implementation(projects.cbox.common.player.director.api)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.sage.common.logging)
}
