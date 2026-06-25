# `:cbox:android:player:emulators:di`

> Metro wiring that assembles the ordered `EmulatorProvider` from every chip emulator.

DI module (`:di`): a Metro `@BindingContainer` that builds the `EmulatorProvider` — the ordered
list of all sound-chip emulators the player can dispatch a file to. Holds no impl of its own; it
references each emulator's `:real` (and the `:fake`) module and orders them so dedicated chiptune
emulators win shared extensions over the broad `vgmstream` catch-all.

## Contents

| File | What it is |
| --- | --- |
| `EmulatorModule.kt` | `@BindingContainer @ContributesTo(AppScope)` object. `@Provides @SingleIn(AppScope)` builds `EmulatorProvider(listOf(...))` over `TwosfEmulator`, `GbaEmulator`, `GmeEmulator`, `NcsfEmulator`, `PsfEmulator`, `SsfEmulator`, `VgmEmulator`, `UsfEmulator`, then `VgmstreamEmulator` (last real — broad catch-all), then `FakeEmulator`. |

## Why depend on this module

The app graph depends on this `:di` module so an `EmulatorProvider` can be resolved. The
generator (`:cbox:android:player:generator:di`) injects the provider's emulator list to render
audio. The ordering here is load-bearing: it decides which emulator claims a file extension.
Depend on `:cbox:common:player:emulators:api` for the `Emulator`/`EmulatorProvider` types.

## Using it

```kotlin
// Resolved from the graph; the generator consumes the emulator list:
class GeneratorModule {
    fun provideRealGenerator(emulatorProvider: EmulatorProvider, /* ... */) =
        RealGenerator(/* ... */ emulatorProvider.emulators, /* ... */)
}
```

## Module facts

- **Plugin:** `sage.android` + `sage.di`
- **Targets:** Android only
- **Source set:** `src/main/java`
- **SAGE/module dependencies:** `:cbox:common:player:emulators:api`, `:cbox:common:player:emulators:fake`, and the `:real` modules for `gba`, `gme`, `ncsf`, `psf`, `ssf`, `twosf`, `usf`, `vgm`, `vgmstream`
