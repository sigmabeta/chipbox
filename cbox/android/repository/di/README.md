# `:cbox:android:repository:di`

> Metro wiring that selects and constructs the app's `Repository` (DB / memory / random).

DI module (`:di`): a Metro `@BindingContainer` that extracts the DAOs from `ChipboxDatabase` to
build a `DatabaseRepository`, and picks the active `Repository` impl from the debug
"repository source" setting — `REAL` (on-device DB), `MEMORY`, or `RANDOM`. Holds no impl of its
own; it wires the `:real` and `:fake` repositories at the DI seam.

## Contents

| File | What it is |
| --- | --- |
| `DatabaseRepositoryModule.kt` | `@BindingContainer @ContributesTo(AppScope)` object. `provideDatabaseRepository` constructs `DatabaseRepository` from the `ChipboxDatabase` DAOs; `provideRepository` reads `DebugSettingsManager.getRepositorySource()` once at graph build and returns the `DatabaseRepository`, `MemoryRepository`, or `RandomMemoryRepository`. The selection takes effect on next launch (no runtime swapping). |

## Why depend on this module

The app graph depends on this `:di` module so a `Repository` can be resolved. It is the seam that
pulls in the concrete Room database (`:cbox:common:database:real`): `repository:real` depends only
on `database:api` (DAO interfaces), so the concrete DB and the fake repositories are joined here.
Depend on `:cbox:common:repository:api` for the `Repository` type.

## Using it

```kotlin
// Resolved from the graph; selection is fixed at launch from debug settings:
class LibraryViewModel @Inject constructor(repository: Repository)
```

## Module facts

- **Plugin:** `sage.android` + `sage.di`
- **Targets:** Android only
- **Source set:** `src/main/java`
- **SAGE/module dependencies:** `:cbox:common:repository:api`, `:cbox:common:repository:real`, `:cbox:common:repository:fake`, `:cbox:common:database:real`, `:cbox:common:debug:api`
