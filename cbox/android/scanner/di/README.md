# `:cbox:android:scanner:di`

> Metro wiring that assembles the library `Scanner` from its repository, content source, readers, and native subsong prober.

DI module (`:di`): a Metro `@BindingContainer` that constructs the `RealScanner` and its
`Readers`, picking the concrete Android `LocalFileContentSource` and the native `VgmstreamProbe`
to satisfy `scanner:real`'s platform-neutral interfaces. The package is
`net.sigmabeta.chipbox.scanner.real` and the class is `RealScannerModule`; despite the name it is
DI wiring only, not the scanner impl (which lives in `:cbox:common:scanner:real`).

## Contents

| File | What it is |
| --- | --- |
| `RealScannerModule.kt` | `@BindingContainer @ContributesTo(AppScope)` object. `provideReaders` builds `Readers`; `provideRealScanner` builds `RealScanner` from `Repository`, the Android `LocalFileContentSource`, `Readers`, the `VgmstreamProbe` (native subsong probe), and `Hatchet`, exposed as `Scanner`. |

## Why depend on this module

The app graph depends on this `:di` module so a `Scanner` can be resolved on Android. It is the
seam that selects the concrete `LocalFileContentSource` (from `contentsource:file:real`) and the
native `VgmstreamProbe` (from `player:emulators:vgmstream:real`) for the otherwise
platform-neutral `scanner:real`. Depend on `:cbox:common:scanner:api` for the `Scanner` type.

## Using it

```kotlin
// Resolved from the graph; the rescan flow injects it:
class RescanManager @Inject constructor(scanner: Scanner)
```

## Module facts

- **Plugin:** `sage.android` + `sage.di`
- **Targets:** Android only
- **Source set:** `src/main/java`
- **SAGE/module dependencies:** `:cbox:common:scanner:api`, `:cbox:common:scanner:real`, `:cbox:common:contentsource:file:real`, `:cbox:common:player:emulators:vgmstream:real`
