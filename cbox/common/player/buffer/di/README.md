# `:cbox:common:player:buffer:di`

> Metro bindings that wire `RealBufferManager` into the app graph.

The `:di` module for the Buffer stage. It contributes one `@BindingContainer` to
`AppScope` that binds the single `RealBufferManager` to its three `:api`
interfaces. As a `:di` module it carries DI wiring only — the app includes it and
nothing depends on it directly.

## Contents

| File | What it is |
| --- | --- |
| `BufferModule.kt` | `@BindingContainer @ContributesTo(AppScope::class) object`. Provides one `@SingleIn(AppScope::class)` `RealBufferManager` (from the injected `Hatchet`), then binds that same instance as `ProducerBufferManager`, `ConsumerBufferManager`, and `BufferDebugSource`. |

## Why depend on this module

You don't — depend on `:cbox:common:player:buffer:api` for the types. The app graph
includes `:di` so Metro can satisfy injections of `ProducerBufferManager` (generator),
`ConsumerBufferManager` (speaker), and `BufferDebugSource` (debug status UI), all
backed by one shared `RealBufferManager`. Binding all three to the same singleton is
what makes the producer and consumer talk to the same queue.

## Using it

```kotlin
// In the AppScope dependency graph, BufferModule is contributed automatically.
// Consumers just inject the interface they need:
@Inject
class RealGenerator(private val bufferManager: ProducerBufferManager) // ...

@Inject
class RealSpeaker(private val bufferManager: ConsumerBufferManager)    // ...
```

## Module facts

- **Plugin:** `sage.jvm` + `sage.di`
- **Targets:** JVM only (Metro DI glue; `src/main/java`)
- **Source set:** `src/main/java`
- **SAGE/module dependencies:** `:cbox:common:player:buffer:api` (`api`), `:cbox:common:player:buffer:real` (`api`), `sage.common.logging`
