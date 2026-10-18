# `:cbox:common:player:emulators:api`

> The `Emulator` contract every chiptune backend implements, plus the provider and scan-time probe types.

The `:api` module: the multiplatform interfaces/types the player and scanner
depend on, with no native code of their own. Concrete backends live in the
per-format `:real` modules; this module only defines what they must implement.

## Contents

| File | What it is |
| --- | --- |
| `Emulator.kt` | Abstract base for every backend. Subclasses implement `loadNativeLib`, `loadTrackInternal`, `generateBufferInternal`, `teardownInternal`, `getLastError`, `getSampleRateInternal`, and `supportedFileExtensions`. The base tracks the per-track frame budget (declared length + fade), flips `trackOver`, and guards `generateBuffer` from crossing into native unless a track is loaded (the use-after-free fix). Optional hooks: `ensureNativeLibReady` (async WASM load), `supportsExtension` (suspend-aware extension probe; defaults to `isFileExtensionSupported`, overridden by web vgmstream to load its module first), `getDiagnostics`, `setTrackNumber`. |
| `EmulatorSelector.kt` | The one ordered selection seam used by both `PcmTrackSource.Factory` implementations and the A/B renderer. `select(emulators, extension)` is `firstOrNull` over `Emulator.supportsExtension`, preserving DI order: dedicated backends win shared extensions and a lazy catch-all (web vgmstream) loads only when its turn comes. Load failures propagate. |
| `EmulatorProvider.kt` | A `data class` wrapping `List<Emulator>` — the set of backends in the current build variant. Wrapped in a class so DI can distinguish the binding from any other `List<Emulator>`. |
| `vgmstream/VgmstreamProber.kt` | Scan-time bridge for vgmstream: the `VgmstreamProber` interface (`isSupported`, `probe`) plus the `VgmstreamSubsong` data class. Kept in `commonMain` so the scanner stays platform-agnostic; the native impl lives in `vgmstream/real`. |
| `EmulatorLoadGuardTest.kt` (test) | Regression guard for the native use-after-free: asserts `generateBuffer` runs native only after a successful load, and skips it after teardown or a failed load. |
| `WasmLoadFailure.kt` | Pure classification (and actionable message) for browser emulator-module load failures: timeout, script `error`, missing factory global, factory rejection, module rejection. Every message names the module URL and says the bundle may lack WASM. The web loader in `apps/js` throws it; the selector lets the load failure propagate. |
| `WasmLoadFailureTest.kt` (test) | Asserts each failure kind names the module + URL + bundle remedy and keeps its detail, and that the observation helper maps script-error/missing-factory correctly. |
| `EmulatorSelectionTest.kt` (test) | Selection-order regression: a shared extension picks its dedicated backend without loading the lazy one, a lazy-only extension loads once and then matches, repeated selection does not reload, and a load failure propagates. Runs under `jvmTest` and `jsNodeTest`. |

## Why depend on this module

Depend on `:api` for the `Emulator` base, `EmulatorProvider`, and the vgmstream
prober types — anything that drives emulators (the generator, cache factory) or
probes vgmstream subsongs at scan time. The app wires the concrete backends from
the per-format `:real` modules and the `:fake` synth via `:di`; nothing here
loads a native library.

## Using it

```kotlin
class MyBackend : Emulator() {
    override fun loadNativeLib() = System.loadLibrary("mycore")
    override val supportedFileExtensions = listOf("xyz")

    external override fun loadTrackInternal(path: String)
    external override fun generateBufferInternal(buffer: ShortArray, framesPerBuffer: Int): Int
    external override fun teardownInternal()
    external override fun getLastError(): String?
    external override fun getSampleRateInternal(): Int
}

// The ordered selector picks a backend by extension (dedicated first, lazy catch-all last):
val emulator = EmulatorSelector.select(provider.emulators, "spc")
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `chipbox.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (+ `commonTest`)
- **SAGE/module dependencies:** `:cbox:common:models:api`, `:cbox:common:player:common:api`, `kotlinx-coroutines-core`, `sage.common.logging`
