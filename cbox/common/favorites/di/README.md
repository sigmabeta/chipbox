# `:cbox:common:favorites:di`

> Metro wiring for favorites — binds the repository into `AppScope`.

The `:di` module for favorites. Its Metro `@BindingContainer` provides the
`FavoritesRepository` from the `FavoritesDatabase` DAOs, or the in-memory fake
when the debug menu selects it. The app graph includes this module; the
`FavoritesDatabase` itself is provided per-platform.

## Contents

| File | What it is |
| --- | --- |
| `FavoritesModule.kt` | `@BindingContainer @ContributesTo(AppScope::class)`. Provides `FavoritesRepository` (`@SingleIn(AppScope::class)`): REAL `RealFavoritesRepository` vs FAKE `FakeFavoritesRepository`, chosen once at graph build from `DebugSettingsManager.getFavoritesSource()`. |

## Why depend on this module

The app graph includes `:di` to get favorites wired into `AppScope`. It pulls in
`:cbox:common:favorites:real` and `:cbox:common:favorites:fake`; the debug switch
picks between them at graph build (applied on the next launch).

## Using it

```kotlin
// Included transitively via @ContributesTo(AppScope::class); consumers inject the bound type:
@Inject class GameDetailViewModel(favorites: FavoritesRepository)
```

## Module facts

- **Plugin:** `sage.jvm` + `sage.di`
- **Targets:** JVM (the DI wiring is plain JVM; the bound impls are KMP)
- **Source set:** `src/main/java`
- **SAGE/module dependencies:** `:cbox:common:favorites:api`, `:cbox:common:favorites:real`, `:cbox:common:favorites:fake`, `:cbox:common:debug:api`, `kotlinx-coroutines-core`, `sage.common.logging`
