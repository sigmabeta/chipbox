# `:cbox:android:coroutines:api`

> The app's `SageScheduler` implementation plus its Metro binding.

Android system glue (`:api`): provides Chipbox's concrete `SageScheduler` — the SAGE list
pipeline's scheduling seam — and the Metro binding that exposes it into `AppScope`. This
module holds both the impl (`ChipboxScheduler`) and its `:di` wiring; the `:api` role here is
"platform-flavoured implementation of a SAGE interface, plus binding".

## Contents

| File | What it is |
| --- | --- |
| `ChipboxScheduler.kt` | `@SingleIn(AppScope)` `SageScheduler` impl. Wraps the injected `CoroutineScope` + `SageDispatchers`; its `DelayManager` always returns `shouldDelay() = false` (no artificial list delays). |
| `di/SchedulerModule.kt` | `@ContributesTo(AppScope)` binding container; `@Provides` the `ChipboxScheduler` as `SageScheduler`. |

## Why depend on this module

The app graph depends on this `:di`-bearing `:api` module so the SAGE list/ViewModel pipeline
(`SageScheduler`) can be resolved. ViewModels and list machinery from `sage.common.list` need a
`SageScheduler`; this supplies the Chipbox one.

## Using it

```kotlin
// Resolved from the graph; consumed by SAGE list ViewModels:
class SomeListViewModel @Inject constructor(
    scheduler: SageScheduler,
)
```

## Module facts

- **Plugin:** `sage.android` + `sage.di`
- **Targets:** Android only
- **Source set:** `src/main/java`
- **SAGE/module dependencies:** `sage.common.list`, `sage.common.coroutines`, `:cbox:common:repository:api`, `:cbox:common:contentsource:file:real`, `:cbox:common:models:api`
