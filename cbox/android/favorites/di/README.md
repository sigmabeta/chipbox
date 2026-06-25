# `:cbox:android:favorites:di`

> Metro wiring that builds the Room `FavoritesDatabase` into `AppScope`.

DI module (`:di`): a Metro `@BindingContainer` that constructs the Android Room instance of
`FavoritesDatabase` (from `:cbox:common:favorites:real`) and provides it as a
`@SingleIn(AppScope)` singleton. Holds no impl of its own — only the `Room.databaseBuilder`
wiring.

## Contents

| File | What it is |
| --- | --- |
| `FavoritesDatabaseModule.kt` | `@BindingContainer @ContributesTo(AppScope)` object. `@Provides @SingleIn(AppScope)` builds `FavoritesDatabase` via `Room.databaseBuilder(context, ..., "chipbox-favorites-database")` with `fallbackToDestructiveMigration()`. |

## Why depend on this module

Depend on this `:di` module from the app graph to make `FavoritesDatabase` resolvable on
Android. Depend on `:cbox:common:favorites:api`/`:real` directly for the favorites types and
DAOs.

## Using it

```kotlin
// Wired automatically once the app graph includes this module:
class Thing @Inject constructor(db: FavoritesDatabase)
```

## Module facts

- **Plugin:** `sage.android` + `sage.di`
- **Targets:** Android only
- **Source set:** `src/main/java`
- **SAGE/module dependencies:** `:cbox:common:favorites:api`, `:cbox:common:favorites:real`
