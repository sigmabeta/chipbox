# `:cbox:common:player:resampler:di`

> Metro bindings that expose the resampler kernels keyed by `ResamplerMode`.

The `:di` module for the resampler stage. It contributes one `@BindingContainer`
to `AppScope` that provides a `Map<ResamplerMode, Resampler>` of the single-
technique kernels from `:real`. As a `:di` module it carries DI wiring only — the
app includes it and nothing depends on it directly.

## Contents

| File | What it is |
| --- | --- |
| `ResamplerModule.kt` | `@BindingContainer @ContributesTo(AppScope::class) object`. Provides one `@SingleIn(AppScope::class)` `Map<ResamplerMode, Resampler>` mapping `ResamplerMode.LINEAR → LinearResampler()` and `ResamplerMode.CUBIC → CubicResampler()`. `ResamplerMode.OS` has no entry — a sink reads that as "bypass / let the OS resample". |

## Why depend on this module

You don't — depend on `:cbox:common:player:resampler:api` for the `Resampler`
type. The app graph includes `:di` so Metro can satisfy a sink's injected
`Map<ResamplerMode, Resampler>`; the sink then picks the active kernel from the
live `ResamplerMode` setting (and bypasses for `OS`, which the map omits).

## Using it

```kotlin
// In the AppScope dependency graph, ResamplerModule is contributed automatically.
// A sink injects the keyed map and selects from the live setting:
@Inject
class RealSpeaker(private val resamplers: Map<ResamplerMode, Resampler>) {
    fun resamplerFor(mode: ResamplerMode): Resampler? = resamplers[mode] // null = OS / bypass
}
```

## Module facts

- **Plugin:** `sage.jvm` + `sage.di`
- **Targets:** JVM only (Metro DI glue; `src/main/java`)
- **Source set:** `src/main/java`
- **SAGE/module dependencies:** `:cbox:common:player:resampler:real` (`api`), `:cbox:common:player:resampler:api` (`implementation`), `:cbox:common:settings:api` (`implementation`, for `ResamplerMode`)
