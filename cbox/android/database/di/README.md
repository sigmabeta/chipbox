# `:cbox:android:database:di`

> Metro wiring that builds the Room `ChipboxDatabase` (the main library DB) into `AppScope`.

DI module (`:di`): a Metro `@BindingContainer` that constructs the Android Room instance of
`ChipboxDatabase` (artists/games/tracks/search history) from `:cbox:common:database:real` and
provides it as a `@SingleIn(AppScope)` singleton. Holds no impl of its own — only the
`Room.databaseBuilder` wiring.

## Contents

| File | What it is |
| --- | --- |
| `DatabaseModule.kt` | `@BindingContainer @ContributesTo(AppScope)` object. `@Provides @SingleIn(AppScope)` builds `ChipboxDatabase` via `Room.databaseBuilder(context, ..., "chipbox-room-database")` with `fallbackToDestructiveMigration()`. |

## Why depend on this module

Depend on this `:di` module from the app graph to make `ChipboxDatabase` resolvable. The
DAOs it exposes are consumed by `:cbox:android:repository:di` to construct `DatabaseRepository`.
Depend on `:cbox:common:database:api`/`:real` directly for the types/DAOs.

## Using it

```kotlin
// Wired automatically once the app graph includes this module:
class Thing @Inject constructor(database: ChipboxDatabase) {
    val games = database.gameDao()
}
```

## Module facts

- **Plugin:** `sage.android` + `sage.di`
- **Targets:** Android only
- **Source set:** `src/main/java`
- **SAGE/module dependencies:** `:cbox:common:database:api`, `:cbox:common:database:real`
