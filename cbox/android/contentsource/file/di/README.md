# `:cbox:android:contentsource:file:di`

> Metro wiring that registers the Android `LocalFileContentSource` into the app's content-source set.

DI module (`:di`): a Metro `@ContributesTo(AppScope)` binding container that provides the
Android `LocalFileContentSource` (from `:cbox:common:contentsource:file:real`) and folds it
into the multibinding `Set<ContentSource>` and the `ContentSourceRegistry`. Holds no impl of
its own — it only wires the shared JVM/Android `:real` impl into `AppScope`.

## Contents

| File | What it is |
| --- | --- |
| `AndroidFileContentSourceModule.kt` | `@ContributesTo(AppScope)` interface. `@Binds @IntoSet` aliases `LocalFileContentSource` into `Set<ContentSource>`; `@Multibinds` declares that set; the companion `@Provides` builds a `@SingleIn(AppScope)` `LocalFileContentSource` backed by `filesDir/library-locations.txt`, and builds the `ContentSourceRegistry` from the set. |

## Why depend on this module

The app's graph depends on this `:di` module so the Android build can resolve `ContentSource`,
the `Set<ContentSource>` multibinding, and `ContentSourceRegistry`. Android dropped SAF in favour
of `MANAGE_EXTERNAL_STORAGE` + raw paths, so it reuses the JVM's `LocalFileContentSource`; the JVM
app wires its own equivalent in `JvmModules`. Depend on `:cbox:common:contentsource:file:real`
directly only for the types.

## Using it

```kotlin
// Wired automatically once the app graph includes this module; consumers inject:
class Generator @Inject constructor(
    private val registry: ContentSourceRegistry,
)
```

## Module facts

- **Plugin:** `sage.android` + `sage.di`
- **Targets:** Android only
- **Source set:** `src/main/java`
- **SAGE/module dependencies:** `:cbox:common:contentsource:file:real`
