# `:cbox:common:player:emulators:di`

> Metro bindings that contribute the `FakeEmulator` into the app graph.

The `:di` module: the Metro `BindingContainer` that provides emulator bindings
into `AppScope`. Today it provides the in-process `FakeEmulator` synth; the
native `:real` backends are wired elsewhere (each `:real` is referenced directly
where the `EmulatorProvider` is assembled).

## Contents

| File | What it is |
| --- | --- |
| `EmulatorsModule.kt` | `@BindingContainer @ContributesTo(AppScope::class) object EmulatorsModule` with a single `@Provides @SingleIn(AppScope::class) fun provideFakeEmulator(): FakeEmulator`. |

## Why depend on this module

Wire `:di` into the app graph (it auto-aggregates via `@ContributesTo(AppScope::class)`)
so `FakeEmulator` is available as an injectable singleton. Depend on `:api` for
the `Emulator` types and on the per-format `:real` modules for the native
backends; this module is the Metro glue, not an entry point you call.

## Using it

```kotlin
// Contributed automatically into the AppScope graph; nothing to call directly.
@BindingContainer
@ContributesTo(AppScope::class)
object EmulatorsModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideFakeEmulator(): FakeEmulator = FakeEmulator
}
```

## Module facts

- **Plugin:** `sage.jvm` + `sage.di`
- **Targets:** JVM only
- **Source set:** `src/main/java`
- **SAGE/module dependencies:** `:cbox:common:player:emulators:api`, `:cbox:common:player:emulators:fake` (both `api`)
