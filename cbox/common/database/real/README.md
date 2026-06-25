# `:cbox:common:database:real`

> The production Room `ChipboxDatabase` (KMP).

The actual Room `@Database` for the library: it binds the entities from
`:cbox:common:entities:api` to the DAO contracts from
`:cbox:common:database:api` and is the implementation the app runs against. This
is the `:real` module — the production storage layer. Room 2.7+ multiplatform
support shares the single `@Database` definition across Android (framework
SQLite) and JVM (bundled SQLite driver); KSP runs `room-compiler` per target so
the generated code lands in `androidMain` and `jvmMain` respectively.

## Contents

| File | What it is |
| --- | --- |
| `ChipboxDatabase.kt` | The `@Database` (version 10) over the six entities + two join tables, exposing all six DAOs (`artistDao()`, `gameDao()`, `trackDao()`, `gameArtistDao()`, `trackArtistDao()`, `searchHistoryDao()`). Declares the `expect object ChipboxDatabaseConstructor` that Room generates a per-target `actual` for via `@ConstructedBy`. |

Schema notes from the source: v8 adds a unique `folder_key` plus unique
constraints on tracks/artists; v9 adds `folder_signature` for
skip-unchanged-folder rescans; v10 adds optional descriptive metadata columns.
The DB is a derived cache, so upgrades use `fallbackToDestructiveMigration`
(rebuilt on the next scan).

## Why depend on this module

Depend on `:cbox:common:database:real` only where you build the actual database
instance — that wiring lives behind the repository layer. Most code should
depend on `:cbox:common:database:api` for the DAO/`@Database` types instead, and
tests use `:cbox:common:database:fake`. These database modules have no `:di`
sibling: the repository `:di` (`:cbox:common:repository:di`) is what constructs
the real `ChipboxDatabase` and wires it into Metro's `AppScope`. Android builds
the database via the `databaseBuilder(Context, …)` overload (framework SQLite);
JVM uses the bundled SQLite driver (`sqlite-bundled`, `jvmMain` only).

## Using it

The `@Database` is abstract and constructed per platform via Room's builder. On
Android, behind the repository `:di`:

```kotlin
val db = Room.databaseBuilder<ChipboxDatabase>(context, name = "chipbox.db")
    .fallbackToDestructiveMigration(dropAllTables = true)
    .build()

val artistDao = db.artistDao()
```

## Module facts

- **Plugin:** `sage.kmp` + `ksp`
- **Targets:** Android + JVM (Room KMP; KSP runs `room-compiler` for `kspAndroid` and `kspJvm`)
- **Source set:** `jvmSharedMain` (`src/main/java`) for the shared `@Database`; `jvmMain` for the bundled SQLite driver
- **SAGE/module dependencies:** `:cbox:common:database:api`, `:cbox:common:entities:api`, `room-runtime`, `sqlite-bundled` (JVM), `room-compiler` (KSP)
