package net.sigmabeta.chipbox.player.emulators.gme

import net.sigmabeta.chipbox.player.emulators.Emulator

/**
 * JVM twin of the Android `:cbox:android:player:emulators:gme:real` wrapper. The Kotlin is
 * byte-identical on purpose: the native JNI symbols in `libgme.so` are bound to this exact
 * fully-qualified class name, so the package/class must match the Android module. The only
 * difference between the two targets is who builds the `.so` (Android: NDK via AGP's
 * externalNativeBuild; JVM: host CMake into `apps/jvm/libs`) — the bytes loaded by
 * [System.loadLibrary] are otherwise the same source.
 */
object GmeEmulator : Emulator() {
    override fun loadNativeLib() {
        System.loadLibrary("gme")
    }

    override val supportedFileExtensions = listOf(
        "gbs",
        "nsf",
        "nsfe",
        "spc"
    )

    private var trackNumber: Int = 0

    override fun setTrackNumber(number: Int) {
        this.trackNumber = number
    }

    override fun loadTrackInternal(path: String) {
        hatchet.d("Starting GME track $trackNumber from $path")
        loadTrackInternalWithNumber(path, trackNumber)
    }

    external override fun generateBufferInternal(buffer: ShortArray, framesPerBuffer: Int): Int

    external override fun teardownInternal()

    external override fun getLastError(): String?

    external override fun getSampleRateInternal(): Int

    private external fun loadTrackInternalWithNumber(path: String, trackNumber: Int)
}
