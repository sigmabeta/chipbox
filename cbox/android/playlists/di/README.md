# `:cbox:android:playlists:di`

> Metro wiring that builds the Room `PlaylistsDatabase` into `AppScope`.

DI module (`:di`): a Metro `@BindingContainer` that constructs the Android Room instance of
`PlaylistsDatabase` (from `:cbox:common:playlists:real`) and provides it as a
`@SingleIn(AppScope)` singleton. Holds no impl of its own — only the `Room.databaseBuilder`
wiring.

## Contents

| File | What it is |
| --- | --- |
| `PlaylistsDatabaseModule.kt` | `@BindingContainer @ContributesTo(AppScope)` object. `@Provides @SingleIn(AppScope)` builds `PlaylistsDatabase` via `Room.databaseBuilder(context, ..., "chipbox-playlists-database")` with `fallbackToDestructiveMigration(dropAllTables = true)`. |

## Why depend on this module

Depend on this `:di` module from the app graph to make `PlaylistsDatabase` resolvable on
Android. Depend on `:cbox:common:playlists:api`/`:real` directly for the playlist types/DAOs.

## Using it

```kotlin
// Wired automatically once the app graph includes this module:
class Thing @Inject constructor(db: PlaylistsDatabase)
```

## Module facts

- **Plugin:** `sage.android` + `sage.di`
- **Targets:** Android only
- **Source set:** `src/main/java`
- **SAGE/module dependencies:** `:cbox:common:playlists:api`, `:cbox:common:playlists:real`
