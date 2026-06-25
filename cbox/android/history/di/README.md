# `:cbox:android:history:di`

> Metro wiring that builds the Room `HistoryDatabase` into `AppScope`.

DI module (`:di`): a Metro `@BindingContainer` that constructs the Android Room instance of
`HistoryDatabase` (playback history, from `:cbox:common:history:real`) and provides it as a
`@SingleIn(AppScope)` singleton. Holds no impl of its own — only the `Room.databaseBuilder`
wiring.

## Contents

| File | What it is |
| --- | --- |
| `HistoryDatabaseModule.kt` | `@BindingContainer @ContributesTo(AppScope)` object. `@Provides @SingleIn(AppScope)` builds `HistoryDatabase` via `Room.databaseBuilder(context, ..., "chipbox-history-database")` with `fallbackToDestructiveMigration()`. |

## Why depend on this module

Depend on this `:di` module from the app graph to make `HistoryDatabase` resolvable on Android.
Its records feed the `PlaybackHistoryRecorder` the playback service observes. Depend on
`:cbox:common:history:api`/`:real` directly for the history types/DAOs.

## Using it

```kotlin
// Wired automatically once the app graph includes this module:
class Thing @Inject constructor(db: HistoryDatabase)
```

## Module facts

- **Plugin:** `sage.android` + `sage.di`
- **Targets:** Android only
- **Source set:** `src/main/java`
- **SAGE/module dependencies:** `:cbox:common:history:api`, `:cbox:common:history:real`
