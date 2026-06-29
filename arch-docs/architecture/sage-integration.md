# Chipbox ↔ SAGE integration

SAGE is a reusable app scaffold vendored as a git **submodule** at `sage/`.
Chipbox builds on top of it: SAGE supplies the build logic, version catalog,
logging, the list/state UI framework, navigation/string/icon contracts, and DI
scope marker; chipbox supplies the domain (player core, features, screens).

## Submodule + build wiring

`sage/` is a separate repo (`.gitmodules` → `github.com:sigmabeta/sage.git`).
It is **not** a Maven publication — it's pulled in as Gradle composite builds in
`settings.gradle.kts`:

```kotlin
includeBuild("build-logic")
includeBuild("sage/sage-build-logic")   // the sage.* convention plugins
includeBuild("sage")                     // the sage library modules
// …
versionCatalogs { create("libs") { from(files("$settingsDir/sage/gradle/libs.versions.toml")) } }
```

So: the **single version catalog** for the whole project is
`sage/gradle/libs.versions.toml`. Bumping a dependency happens there. Changes to
sage build logic / libraries are **separate commits in the sage submodule**; a
chipbox commit that depends on them must also stage the updated submodule pointer
(`git add sage`). After cloning: `git submodule update --init --recursive`.

## What SAGE provides

`sage/common/*` (KMP, mostly hoisted to `commonMain`):

- `logging` — `Hatchet` logger interface (+ `BasicHatchet`/`BluntHatchet`).
- `appcomm` — `SageAction` (sealed action base), `ActionSink`, `LCE<T>`
  (Loading/Content/Error/Uninitialized).
- `list` — `ListState` (abstract), `ListStateActual`, `ColumnType`, `WidthClass`.
- `ui/components` — `ListModel` sealed hierarchy + `TitleBarModel`,
  `LoadingItemListModel`, `ErrorStateListModel`, `IconNameListModel`, etc.
- `ui/list-screens` — the `ListScreen` / `GridScreen` Compose-Multiplatform
  renderers + `LocalListBottomInset`.
- `ui/strings` — `StringProvider` interface + `SageStringId`.
- `ui/icons-api` — `Icon` contract (Compose-free).
- `di` — `AppScope` marker class (private ctor) referenced by FQCN in Metro
  annotations. Lives in sage to avoid a sage→chipbox cycle.
- plus `nav` (`RouteDescriptor`, `ArgType`), `coroutines` (`SageDispatchers`),
  `storage/common` (`Storage`), `appinfo` (`AppInfo`), `analytics`, `events`,
  `connectivity`, `perf`.

`sage/android/*` — Android impls: `AndroidHatchet` (logging), `ui/themes`
(`SageMaterial`), `ui/strings`, `analytics` (Firebase), `coroutines`,
`resources`, `ui/icons-real`. `sage/fake/*` — no-op `analytics`/`perf` for the
JVM classpath.

## Key types chipbox extends

- **Logger**: every VM injects `Hatchet` (sage). Android binds `AndroidHatchet`.
- **List/state framework** (the backbone of every feature screen):
  `ChipboxListViewModel<S : ListState>` (in `cbox/common/ui/list/api`) extends
  sage's lifecycle `ViewModel`; feature `<Name>State` extends sage's `ListState`;
  feature `<Name>Action` extends sage's `SageAction`; rows are sage `ListModel`s;
  rendering goes `ListState.toActual()` → `ListStateActual` →
  `ChipboxListEntry` → sage `ListScreen`/`GridScreen` → `ListModel.Content()`.
  See `feature-screens.md`.
- **Strings**: chipbox defines `ChipboxStringId` (enum) and injects sage's
  `StringProvider`. Android impl reads compiled Compose Multiplatform
  `Res.string.*`; the JVM impl (`apps/jvm/.../JvmStringProvider`) is a
  `Map<SageStringId,String>` generated from the Android `strings-*.xml` by
  `scripts/gen_jvm_strings.py`.
- **Icons**: feature rows pass `Icon.*` (sage `icons-api`) into `IconNameListModel`.
- **DI scope**: everything is scoped to sage's `AppScope`.

## DI — Metro (not Hilt)

> Hilt + plain Dagger + KSP were **removed** in the Metro migration
> (Milestone 6). Don't reintroduce Hilt annotations or
> `@AndroidEntryPoint`/`@HiltViewModel`.

- App graph: `apps/android/.../di/ChipboxAppGraph.kt` —
  `@SingleIn(AppScope::class) @DependencyGraph(AppScope::class) interface
  ChipboxAppGraph : ViewModelGraph`. Built lazily in `ChipboxApplication`
  (`createGraphFactory<ChipboxAppGraph.Factory>().create(this)`).
- Metro keeps the existing **Dagger-shaped** `@Inject`/`@Provides`/`@Module`/
  `@Binds` annotations recognised via `metro { interop.includeDagger() }` in
  `apps/android/build.gradle.kts`, so binding containers weren't rewritten to
  Metro-native syntax. ~21 `@ContributesTo(AppScope::class)` binding containers
  across `cbox`/`features`/`apps` aggregate into the graph automatically.
- ViewModels are multibound into the graph:
  `@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())` +
  `@ViewModelKey` (+ `@Inject` or `@AssistedInject` with a
  `ManualViewModelAssistedFactory`). Compose resolves them with
  `metroViewModel<VM>()` (from `dev.zacsweers.metrox.viewmodel`).
- **No `@AndroidEntryPoint`/`@EntryPoint`.** The four Android entry points
  (`ChipboxApplication`, `MainActivity`, `ChipboxPlaybackService`,
  `ArtworkProvider`) cast `application` to `ChipboxApplication` and read graph
  accessors directly (`app.appGraph.director`, etc.). `MainActivity` provides the
  ViewModel factory into composition via `LocalMetroViewModelFactory`.
- `apps/jvm` has its own Metro graph + JVM-side bindings (`JvmStorage`,
  `JvmStringProvider`, `JvmAppInfoModule`, …) since it can't use the Android-only
  pieces.

## The boundary in one table

| Concern | sage provides | chipbox provides |
|---|---|---|
| Logging | `Hatchet` + `AndroidHatchet` | injects it everywhere |
| Build | `sage.*` plugins, `libs.versions.toml` | `chipbox.*` plugins layered on top |
| List UI | `ListState`, `ListModel`, `ListScreen`/`GridScreen`, `StringProvider`, `Icon` | `ChipboxListViewModel`, `<Name>State/Action`, `ChipboxListEntry`, `ChipboxStringId` |
| DI | `AppScope` marker | `ChipboxAppGraph` + 21 binding containers (Metro) |
| Nav | `RouteDescriptor`/`ArgType` contracts | Voyager wiring in `ChipboxScreens.kt` |
| Theme | `SageMaterial` | `ChipboxTheme` (palette + pixel fonts) |
