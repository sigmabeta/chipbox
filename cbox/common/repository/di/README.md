# `:cbox:common:repository:di`

> Metro bindings that wire the repository into `AppScope`.

The `:di` module for the library repository: a Metro (`dev.zacsweers.metro`)
`@BindingContainer` that contributes repository providers into `AppScope`. The
app includes this module so the repository is resolvable in the dependency graph.

## Contents

| File | What it is |
| --- | --- |
| `RepositoryModule.kt` | `@BindingContainer @ContributesTo(AppScope::class) object RepositoryModule` — provides `MemoryRepository` and (lazily) `RandomMemoryRepository`, each `@SingleIn(AppScope::class)`. |

## Why depend on this module

Include `:cbox:common:repository:di` from the app's dependency graph to make the
repository available for injection. It `api`-exposes `:cbox:common:repository:api`
(the `Repository` type) and `:cbox:common:repository:fake` (the in-memory
sources it binds). Consumers depend on `:api` for the type; this `:di` provides
the instances.

Note: `RepositoryModule` currently provides the in-memory `MemoryRepository` /
`RandomMemoryRepository` debug sources. The production `DatabaseRepository` lives
in `:cbox:common:repository:real`; the platform DI modules own the
repository-source switch that picks which binding satisfies `Repository`.

## Using it

```kotlin
// Contributed automatically into AppScope by @ContributesTo; the app graph
// resolves the bound repository sources without manual wiring:
@DependencyGraph(AppScope::class)
interface AppGraph {
    val memoryRepository: MemoryRepository
}
```

## Module facts

- **Plugin:** `sage.jvm` + `sage.di`
- **Targets:** JVM only (Metro DI glue; `src/main/java`)
- **Source set:** `src/main/java` (`jvmSharedMain`)
- **SAGE/module dependencies:** `api`s `:cbox:common:repository:api` and
  `:cbox:common:repository:fake`
