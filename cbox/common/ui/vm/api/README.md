# `:cbox:common:ui:vm:api`

> The multiplatform `ViewModel` base every Chipbox UI view-model extends.

The `:api` module that defines `ChipboxViewModel`, the common base class for
Chipbox UI view-models. Being `:api`, it is the public surface other UI modules
build on (e.g. `:cbox:common:ui:list:api`'s `ChipboxListViewModel` extends it);
there is no `:real` counterpart — the class is the whole module.

## Contents

| File | What it is |
| --- | --- |
| `ChipboxViewModel.kt` | `open class ChipboxViewModel : ViewModel()` — extends `androidx.lifecycle.ViewModel` from the multiplatform lifecycle artifact, which publishes `ViewModel` + `viewModelScope` for both Android and JVM targets. |

That is the entire module today. `ChipboxViewModel` is an `open` class with no
members of its own; it exists so the rest of the Chipbox UI can share one
`viewModelScope`-bearing base without depending on a platform-specific
`ViewModel`.

> **Note on `build.gradle.kts`:** the long comment block in the build file
> describes an *aspirational* design — a deliberately-empty marker base, a
> `ViewModelProvider` interface, a `LocalViewModelProvider` `CompositionLocal`,
> and a `chipboxViewModel()` helper. **None of those exist in the source today.**
> The marker was since promoted to extend `androidx.lifecycle.ViewModel` (the
> first Android port, `ChipboxListViewModel`, needed `viewModelScope`), and
> ViewModel resolution in feature code is done with Metro's `metroViewModel<VM>()`
> (see `arch-docs/architecture/feature-screens.md`), not a provider from this module.
> Treat the file's source as the source of truth.

## Why depend on this module

Depend on `:cbox:common:ui:vm:api` when you need a Chipbox UI view-model base
with a multiplatform `viewModelScope`. In practice the dependency is transitive:
features extend `ChipboxListViewModel` (or `ChipboxFreeformViewModel`), and the
list-VM base pulls this module in. Extend `ChipboxViewModel` directly only for a
UI view-model that isn't list- or freeform-shaped.

## Using it

```kotlin
class MyScreenViewModel : ChipboxViewModel() {
    init {
        viewModelScope.launch { /* load data, etc. */ }
    }
}
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `sage.compose.kmp`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `androidx.lifecycle.viewmodel` (multiplatform lifecycle artifact). No SAGE or project deps (leaf).
