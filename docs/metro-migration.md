# Hilt → Metro migration — plan & status

Status: **Milestones 3 + 4 implemented. Milestone 5 attempted on `SettingsViewModel`, reverted on Kotlin/Metro version-compat blocker.**

Scope of this doc: how Chipbox moves from Dagger/Hilt to
[Metro](https://github.com/ZacSweers/metro) (Zac Sweers' Kotlin-compiler-plugin
DI library). The decision to migrate was triggered by the Settings UI port:
Milestone 9 slice 5b hit a wall when `features/settings/real` couldn't be flipped
to `sage.kmp` because Hilt's Gradle plugin rejects `com.android.kotlin.multiplatform.library`
("The Hilt Android Gradle plugin can only be applied to an Android project").
Metro is a Kotlin compiler plugin with first-class KMP support across JVM,
Android, JS, Wasm, and Native targets — solves the immediate blocker and replaces
all of Hilt at the same time.

See `docs/kmp-migration.md` for the larger Android → KMP work this is a
prerequisite for; this doc owns the Hilt/Metro story end-to-end.

## Goal

Drop Dagger/Hilt from Chipbox entirely. Land Metro as the single DI library,
with `@DependencyGraph` replacing Hilt's `SingletonComponent` and a chipbox-side
mechanism replacing Hilt's `@HiltViewModel` per-`NavBackStackEntry` factory plumbing.
Net win:

- `features/settings/real` (and anything else that needs to be in a
  `sage.kmp` module) can now apply DI normally — Metro's plugin layers on top of
  `kotlin("multiplatform")` and doesn't care whether the Android library target
  is `com.android.library` or `com.android.kotlin.multiplatform.library`.
- DI annotations available in commonMain (Metro publishes a multiplatform runtime).
- Faster builds: no KSP/KAPT round-trip; Metro processes during the normal
  Kotlin compile.

## Starting point

Hilt footprint at the start of this work (as of the M9 slice 5b commit):

- **51 files** touch Hilt across `apps/`, `cbox/`, `features/`, and `sage/`.
- **35 `@Module` / `@InstallIn`** declarations (mostly `@InstallIn(SingletonComponent::class)`).
- **15 `@HiltViewModel`** classes (one per feature screen).
- **3 entry points**: the `@HiltAndroidApp` Application class, the
  `@AndroidEntryPoint` `MainActivity`, and an `@AndroidEntryPoint`
  `MediaSessionService`.
- One `sage.di.android` convention plugin (`SageDiAndroidModulePlugin`)
  applying KSP + the `dagger-hilt-android` Gradle plugin.
- One hand-rolled `AndroidHiltViewModelProvider` (Milestone 9 slice 3 of the
  KMP migration) wrapping `androidx.hilt.navigation.HiltViewModelFactory` for
  the `chipboxViewModel<T>()` accessor.
- A separate `JvmChipboxComponent` (plain Dagger, no Hilt) for the desktop
  target — that stays plain Dagger for slice 1 of this plan, then converts in
  slice 4 (it's the easier half of the migration since there are no Hilt
  Android entry points to replace).

Metro provides Dagger annotation interop via `metro { interop { includeDagger() } }`,
so existing `@Inject` / `@Provides` / `@Module` / `@Binds` / `@Component` keep
working AS IS during the transition. The Hilt-specific annotations
(`@HiltViewModel`, `@AndroidEntryPoint`, `@HiltAndroidApp`, `@InstallIn`) have
no Metro equivalents and need hand-rolled replacements — *except* `@HiltViewModel`,
which has a direct replacement in **`metrox-viewmodel-compose`** (see below).

### The `metrox-viewmodel-compose` extension

Metro ships a companion library that handles Compose ViewModel integration —
the `@HiltViewModel` + `hiltViewModel<T>()` story end-to-end. Docs:
<https://zacsweers.github.io/metro/1.1.1/metrox-viewmodel-compose/> and the
underlying [`metrox-viewmodel`](https://zacsweers.github.io/metro/1.1.1/metrox-viewmodel/).

Shape:

```kotlin
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class HomeViewModel(...) : ViewModel() { ... }

// At the app root:
CompositionLocalProvider(LocalMetroViewModelFactory provides metroVmf) {
    AppContent()
}

// In any screen composable:
@Composable
fun HomeScreen(viewModel: HomeViewModel = metroViewModel()) { ... }
```

The graph extends `ViewModelGraph` to expose the three multibindings the
framework needs (`viewModelProviders`, `assistedFactoryProviders`,
`manualAssistedFactoryProviders`). `MetroViewModelFactory` is provided by the
framework; the app subclasses or wires one instance.

This collapses what would have been the hardest slice into a mechanical
sweep: every `@HiltViewModel class FooViewModel @Inject constructor(...)`
becomes `@Inject @ViewModelKey @ContributesIntoMap(AppScope::class) class FooViewModel(...)`,
and every `viewModel: FooViewModel = hiltViewModel()` becomes
`viewModel: FooViewModel = metroViewModel()`. Per-`ViewModelStoreOwner`
scoping (the `NavBackStackEntry` clears-with-screen behavior) is preserved
because the framework still routes through AndroidX `ViewModelStore`.

The chipbox-side `chipboxViewModel<T>()` / `LocalViewModelProvider` /
`AndroidHiltViewModelProvider` machinery from M9 slice 3 of the KMP migration
**all gets deleted** at Milestone 3 — the framework's
`metroViewModel<T>()` / `LocalMetroViewModelFactory` is the same shape,
upstream-maintained, and works on both Android and desktop.

### Key design decisions to settle in slice 1

- **Single scope or scope hierarchy?** Hilt has `SingletonComponent`,
  `ActivityComponent`, `ViewModelComponent`, `FragmentComponent`. Chipbox uses
  only `SingletonComponent` today (every `@InstallIn` is `SingletonComponent`).
  Starting point: a single `AppScope` for everything that's currently
  `SingletonComponent`-scoped, with `@SingleIn(AppScope::class)` as the
  equivalent of `@Singleton`. Per-screen scoping comes free via
  `metrox-viewmodel-compose` (above) — no chipbox-side mechanism needed.

- **Aggregation: plain `@DependencyGraph` or contributions?** Hilt aggregates
  modules automatically across the compile classpath via `@InstallIn`. Metro's
  equivalent is `@ContributesTo(AppScope::class)` on a `@Module` interface, or
  `@ContributesBinding(AppScope::class)` on an `@Inject` class. Pick
  contributions — same shape, less repetitive than explicit graph
  `include = [...]` lists with 35 modules.

- **Does `metrox-viewmodel-compose` publish multiplatform artifacts?** The
  documentation page is Android-flavored but the artifact is a Compose
  Multiplatform extension; the parent `metrox-viewmodel` is multiplatform.
  Verify in slice 1 that the JVM target can consume the same composable
  accessor for the desktop UI port.

## Milestones

### Milestone 1 — foundation smoke test (done)

The "is this viable" round. The original plan was to land Metro on
`features/settings/real` (the original Hilt-on-KMP blocker), but that ran
straight into the same wall again: the `dagger-hilt-android` Gradle plugin
rejects `com.android.kotlin.multiplatform.library` *even when* Metro is also
applied, and we can't drop Hilt from that module yet because
`@HiltViewModel SettingsViewModel` still needs Hilt-side processing during
the migration. So slice 1 pivoted to `apps/android` — a smaller-blast-radius
smoke test that validates Metro + Hilt coexistence without involving KMP:

- **Sage catalog**: pinned Metro to 1.1.1; added `metro` plugin alias and
  `metrox-viewmodel` + `metrox-viewmodel-compose` library aliases.
- **`cbox/common/di/api`**: new sage.kmp module hosting `AppScope`. Pure
  marker class with a private constructor — Metro uses the `KClass`
  reference at compile time only. Referenced by `@DependencyGraph`,
  `@SingleIn`, `@ContributesTo`, and `@ContributesBinding` annotations
  going forward.
- **`apps/android` applies the Metro plugin** with
  `metro { interop { includeDagger() } }`. Coexists with the existing
  `hilt-android` Gradle plugin without complaint.
- **`apps/android/.../di/ChipboxAppGraph`**: minimal
  `@DependencyGraph(AppScope::class)` interface with one binding —
  `metroSmokeTest: String` provided as a constant. Stub for the real
  graph that grows in Milestone 2.
- **`MainActivity` instantiates the graph via `createGraph<ChipboxAppGraph>()`**
  and logs its single binding. Confirms Metro generated working code; the
  log line gets deleted in Milestone 2 alongside the stub binding.

This slice doesn't change any production behavior — Hilt still owns every
real binding and entry point. It only proves three things:

1. Metro's compiler plugin coexists with `dagger-hilt-android` in the same
   module.
2. Metro's Dagger annotation interop (`includeDagger()`) recognises our
   existing `@Inject` / `@Provides` / `@HiltViewModel` annotations across
   the compile classpath without breaking them.
3. `createGraph<T>()` and `@DependencyGraph` produce working bytecode in
   the actual app build.

Verified: `:apps:android:assembleDebug` + `:cbox:common:di:api:build` both
green. `features/settings/real` and the KMP-side blocker stay as-is until
Milestone 6.

### Milestone 2 — `ChipboxAppGraph` (done)

Builds the Metro equivalent of `SingletonComponent` with real (no-cross-module-
dependency) bindings, and gives it a stable owner. Hilt continues to do almost
all the work; this slice just establishes the parallel graph.

- **`ChipboxAppGraph`** drops the M1 smoke-test `metroSmokeTest: String`
  binding and gains two real ones:
  - `appInfo: AppInfo` — provides a `BuildConfig`-derived `AppInfo` inline,
    matching `AndroidAppModule.provideAppInfo()`.
  - `hatchet: Hatchet` — provides `AndroidHatchet()` inline, matching
    `AndroidAppModule.provideHatchet()`.

  Both bindings are `@SingleIn(AppScope::class)` (Metro's equivalent of
  `@Singleton` for an `AppScope`-scoped graph), and both are duplicated on
  the Hilt side for now — Hilt consumers (`@Inject lateinit` on
  `MainActivity` etc.) still see Hilt's copies; Metro consumers see Metro's.
  Same `BuildConfig` values + same `AndroidHatchet` singleton class, so the
  two instances are functionally equivalent.

  Bindings with cross-module deps (`StringProvider` needs `@ApplicationContext`,
  `LibrarySource` needs `AndroidFileContentSource` from a separate module,
  `PLAYBACK_STATUS_AVAILABLE` needs `PlaybackStatusEntryPoint`) stay
  Hilt-only this slice — they'll land in `ChipboxAppGraph` in Milestone 4
  when those modules also `@ContributesTo(AppScope::class)`.

- **`ChipboxApplication`** owns the graph: `val appGraph: ChipboxAppGraph by
  lazy { createGraph<ChipboxAppGraph>() }`. Lazy so the graph is built on
  first use, not at process start — cold-start cost stays near zero during
  the migration while Hilt does the heavy lifting. `@HiltAndroidApp` stays
  on the class until Milestone 6.

- **`MainActivity`** moved off the M1-era local `createGraph` call: now
  pulls `(application as ChipboxApplication).appGraph` and logs
  `appGraph.appInfo`. Confirms the graph resolves a real binding from its
  stable owner.

- **`AndroidAppModule`** untouched this slice — the "convert to
  `@ContributesTo(AppScope::class)`" item from the original plan is
  deferred to Milestone 4. Doing it now would require every transitive
  dep's home module to also be Metro-aware, which is the whole point of
  the bulk sweep slice.

Verified: `:apps:android:assembleDebug` green; Hilt and Metro graphs
coexist; `ChipboxApplication.appGraph` resolves correctly at runtime
(verified via the `MainActivity.onCreate` log line).

### Milestone 3 — wire `metrox-viewmodel-compose` framework (done, Android side)

The original M3 plan tried to do both framework wiring AND one VM
conversion in the same slice. Discovered mid-slice that the second part
isn't possible yet: converting `SettingsViewModel` (or any non-trivial VM)
requires *all* its transitive constructor params — Repository, Scanner,
LibrarySource, the @Named flags, etc. — to be Metro-resolvable, which is
exactly M4's bulk module sweep. So M3 shrank to framework wiring only;
the VM conversions move from M3 to M5 once M4 makes the deps available.

Slice landed on the Android target:

- **`apps/android/build.gradle.kts`** picks up `metrox-viewmodel` +
  `metrox-viewmodel-compose` deps.

- **`ChipboxAppGraph`** now extends `ViewModelGraph` from `metrox-viewmodel`.
  That contributes three `@Multibinds(allowEmpty = true)` maps
  (`viewModelProviders`, `assistedFactoryProviders`,
  `manualAssistedFactoryProviders`) and the `metroViewModelFactory:
  MetroViewModelFactory` accessor. All three maps are empty for now —
  they fill in when each VM gets converted in M5.

- **`ChipboxMetroViewModelFactory`** — `@Inject @ContributesBinding(AppScope::class)
  @SingleIn(AppScope::class)` subclass of `MetroViewModelFactory` whose
  three constructor params are the multibinding maps. The framework's
  base class dispatches `create(modelClass, extras)` to the right map at
  runtime.

- **`MainActivity`** wraps `setContent` in
  `CompositionLocalProvider(LocalMetroViewModelFactory provides
  appGraph.metroViewModelFactory)`. `metroViewModel<T>()` calls anywhere
  in the composition will read from this factory once M5 starts
  registering VMs.

- **`features/settings/real`** applies the Metro plugin alongside the
  existing Hilt plugin (with `interop.includeDagger()`). Pulls
  `metrox-viewmodel-compose` so its `metroViewModel<T>()` accessor is
  reachable. `SettingsViewModel` stays `@HiltViewModel` for now — the
  Metro annotations land in M5 once Repository / Scanner / LibrarySource
  modules also `@ContributesTo(AppScope::class)`.

What got tried during the slice but had to revert: a one-shot conversion
of `SettingsViewModel` to `@Inject @ViewModelKey @ContributesIntoMap(AppScope::class)`.
Metro correctly aggregated the contribution but then choked at graph
build time on every constructor arg — 10 `[Metro/MissingBinding]` errors
because Repository / Scanner / LibrarySource / etc. are all still
Hilt-only. The plan's slice ordering was just wrong: framework wiring
(M3) → bulk module sweep (M4) → bulk VM sweep (M5) is the correct
sequence; doing one VM in M3 ahead of M4 doesn't work for any VM with
non-trivial deps.

Plain Dagger interop (`includeDagger()`) does mean we're not blocked on
the M4 sweep — Metro can pick up `@Inject` / `@Provides` annotations from
existing Hilt modules whenever they `@ContributesTo(AppScope::class)`.
M4 is mostly mechanical: every `@Module @InstallIn(SingletonComponent::class)`
gains a sibling `@ContributesTo(AppScope::class)` annotation; the
modules continue to feed Hilt's graph AND start feeding Metro's.

JVM-side work (extending the JVM-side graph with `ViewModelGraph`,
wiring `LocalMetroViewModelFactory` in `DesktopMain`, removing the
hand-rolled `chipboxViewModel<T>()` machinery) is deferred to its own
slice after the Android side is fully Metro. The chipbox-side
`ViewModelProvider` / `chipboxViewModel<T>()` /
`AndroidHiltViewModelProvider` / `JvmViewModelProvider` /
`LocalViewModelProvider` all still exist — they get deleted once both
targets are on `metroViewModel<T>()`.

Verified: `:apps:android:assembleDebug` green with both Hilt and Metro
processing the same module's annotations; the empty-but-declared
`MetroViewModelFactory` is bound from `appGraph.metroViewModelFactory`
and provided at composition root — ready for VMs to plug in.

### Milestone 4 — bulk module sweep (sage modules done; chipbox modules pending)

Mechanical conversion: each `@Module` gains an `@ContributesTo(AppScope::class)`
annotation alongside its existing `@InstallIn(SingletonComponent::class)`,
so the bindings flow into both Hilt's and Metro's graphs during the
transition. Started with the sage submodule — only 4 `@Module` files
there vs. 30+ in chipbox/features, and converting sage first means all
the foundational bindings (Hatchet, coroutines, resources, analytics)
land on the Metro side before chipbox features need them.

#### Sub-slice 4a — sage modules + scope plumbing (done)

- **`AppScope` moved into sage** as a new `sage/common/di` module
  (`net.sigmabeta.sage.di.AppScope`). Was previously in chipbox; sage
  modules couldn't reference it without forcing a sage → chipbox cycle.
  Chipbox-side `cbox/common/di/api` deleted; apps/android and
  features/settings/real now depend on `libs.sage.common.di`.
  `ChipboxAppGraph` + `ChipboxMetroViewModelFactory` import the sage
  flavor.

- **4 sage modules converted** — each gains `dev.zacsweers.metro`
  plugin, `metro { interop { includeDagger() } }`, a `projects.common.di`
  dep for AppScope, and `@ContributesTo(AppScope::class)` on the
  `@Module object` declaration:
  - `sage/android/coroutines` — `SageDispatchers` + `CoroutineScope`.
  - `sage/android/resources` — `Resources` + `ResourceProvider`.
  - `sage/fake/analytics` — `Analytics` (Noop impl).
  - `sage/android/analytics` — `FirebaseAnalytics`. (Build hit an
    *unrelated* pre-existing Firebase catalog issue —
    `firebase-analytics-ktx` got renamed to `firebase-analytics` in
    a newer BOM — not part of this slice; Metro wiring on the module
    is in place.)

  Found inline: **Metro requires explicit return types on `@Provides`
  functions** where Hilt was lax. `CoroutinesModule.provideCoroutineScope`
  and `provideRegularDispatchers` had inferred return types; added
  explicit `: CoroutineScope` / `: SageDispatchers`. Expect more of
  the same in the chipbox sweep.

- **`ChipboxAppGraph` gains a factory + Application binding.** Sage
  `ResourcesModule` and `AnalyticsModule` take `@ApplicationContext
  Context` parameters. Metro recognises Hilt's `@Qualifier` meta-
  annotation via interop, but the binding has to come from somewhere
  — added a `@DependencyGraph.Factory` that takes `Application` as
  `@Provides`, plus a `@Provides @ApplicationContext fun
  provideAppContext(application: Application): Context = application`.
  `ChipboxApplication` switched from `createGraph<…>()` to
  `createGraphFactory<ChipboxAppGraph.Factory>().create(this)`.

  Result: sage's `@ApplicationContext` parameters resolve cleanly from
  Metro's graph at compile time.

Verified: `:apps:android:assembleDebug` green. Hilt and Metro
processing both run on the 4 sage modules without conflict.

#### Sub-slice 4b — chipbox modules (done)

The 27 remaining `@Module` declarations across `cbox/**/di` and
`features/**/di`. Rather than touching each `build.gradle.kts`, the
heavy lift went into the two `sage.di.*` convention plugins so the
Metro plumbing reaches all consumers automatically:

- **`SageDiAndroidModulePlugin`** (sage) now applies the
  `dev.zacsweers.metro` plugin, configures
  `metro.interop.includeDagger()`, and adds the `sage-common-di`
  dep. Every `sage.di.android` consumer (33 chipbox/feature
  modules) gets Metro for free.
- **`SageDiJvmModulePlugin`** (sage) got the same treatment for the
  `cbox/common/*/di` JVM-only modules. These use `hilt-core` instead
  of `hilt-android`; convention plugin still applies Metro alongside.
- **`MetroPluginExtension` on the convention classpath**:
  sage-build-logic's `convention/build.gradle.kts` adds the
  `metro-gradlePlugin` dep as `implementation` (not `compileOnly`) —
  the convention applies the Metro plugin programmatically via
  `pluginManager.apply`, so the plugin's classes have to be on the
  runtime classpath. New `metro-gradlePlugin` catalog alias.
- **27 `@Module` source files** gained `@ContributesTo(AppScope::class)`
  + the two imports (`dev.zacsweers.metro.ContributesTo` and
  `net.sigmabeta.sage.di.AppScope`), applied via the idempotent
  `scripts/add_metro_contributes_to.py` helper (committed). The
  helper scans for `@Module` declarations, adds the annotation
  immediately above the class/object, and inserts the imports in
  alphabetical order relative to the existing block.

Metro-strictness fixes surfaced during the build cycle, expected
from M4a's discovery — caught + fixed in 11 modules:

- **`Implicit return types are not allowed for @Provides`**: Metro
  is stricter than Hilt about inferring binding target types.
  `SpeakerModule` (3 functions), `DatabaseModule`, `EmulatorModule`,
  all 7 emulator-specific submodules (`gba`/`gme`/`psf`/`ssf`/`vgm`/`usf`/`2sf`),
  `MockRepositoryModule` (5), `DatabaseRepositoryModule`,
  `GeneratorModule` (2), `RepositoryModule`, `EmulatorsModule`,
  `BufferModule` — all got explicit return types added.
- **`@Binds declarations may not have scopes`**: Metro forbids
  scopes (e.g. `@Singleton`) on `@Binds` declarations; the
  underlying `@Provides` still has the scope.
  `AndroidFileContentSourceModule` and `PlaybackStatusModule` (real
  variant) had `@Singleton` removed from `@Binds`.

**Critical graph adjustment**: `ChipboxAppGraph` now carries both
`@Singleton` (javax) and `@SingleIn(AppScope::class)` as graph
scopes. Metro treats `javax.inject.Singleton` as a scope distinct
from `AppScope`, and a graph refuses to wire bindings whose scope
doesn't match any of the graph's declared scopes. During the
transition, the contributed `@Module`s use the original `@Singleton`
annotations untouched — letting the graph accept *both* scope
markers means we don't have to mass-rewrite `@Singleton` →
`@SingleIn(AppScope::class)` until M6 drops Hilt entirely. The
inline `provideAppInfo` / `provideHatchet` on the graph also go
away — `AndroidAppModule` (now `@ContributesTo`) provides both, and
having both inline + via aggregation would cause `Metro/DuplicateBinding`.

What was NOT touched in this sub-slice:

- `cbox/android/artworkprovider/api/.../ArtworkProvider.kt` — uses
  `@EntryPoint` (Hilt-specific pattern for non-Hilt-managed Android
  components like `ContentProvider`s); converts in M6 when entry
  points get their Metro equivalents.
- `AndroidAppModule`'s `@Named` playback-status bindings + `LibrarySource`
  binding: still Hilt-side only. They're consumed by `SettingsViewModel`
  (a `@HiltViewModel`); once M5 converts that VM, those bindings will
  be needed via Metro and either get migrated or stay duplicated.

Verified: `:apps:android:assembleDebug` green; Metro processes all
27 module contributions alongside Hilt's KSP. Metro graph is
non-trivially populated now — about ~40 contributed bindings
(everything from sage M4a + chipbox M4b). `ChipboxAppGraph` doesn't
yet expose accessors for most of them, but they're available
should M5's VM conversions need them.

#### Sub-slice 4c — JVM-side (pending)

`apps/jvm`'s plain-Dagger `JvmChipboxComponent` + `JvmModules.kt` gets
the same treatment, producing one shared module set that contributes
to both the Android `ChipboxAppGraph` and a JVM-side `JvmChipboxGraph`.

### Milestone 5 — VM sweep (paused on version compat)

Per-feature VM migration. Pivoted from the original "bulk sweep" plan
to **screen-by-screen** after attempting `SettingsViewModel` and
finding the all-at-once approach fights Metro's transitive aggregation
across variant-specific `debugImplementation` chains (see commit
notes). The new slicing rules — recorded in user memory for the rest
of the migration:

- Migrate one feature at a time.
- When the feature depends on another feature's `api`, *drop that dep*
  during the migration. Delete the now-broken state fields / VM params
  / `@Named` bindings.
- If a `SageAction` would navigate to a not-yet-migrated screen,
  replace `emit(ChipboxEvent.NavigateTo(...))` with a `hatchet.w(...)`
  + `ShowSnackbar("Not implemented yet")`.
- Re-add the dep as each dependency screen itself migrates.

This keeps each feature's Metro graph self-contained — no
transitive-resolution gymnastics.

#### Sub-slice 5a — Settings de-coupling (done, VM still @HiltViewModel)

Applied the screen-by-screen rules to Settings, but reverted the
`@HiltViewModel` → Metro conversion on a separate version-compat
blocker (see below). The cleanup itself stays — when the compat issue
is resolved, the VM converts on top of an already-trimmed feature.

- `features/settings/real`'s `implementation(projects.features.playbackStatus.api)`
  dropped. Adjacent `features/playback-status/{real,fake}/di/PlaybackStatusModule`
  files have their `@Singleton`-on-`@Binds` removed (Metro forbids
  scopes on `@Binds`); the real variant's module also rewrote
  `@Binds` → `@Provides` on `object` (Metro's Dagger interop doesn't
  pick up `@Binds` on `abstract class` reliably).
- `SettingsViewModel`: lost two constructor params
  (`@Named(PLAYBACK_STATUS_AVAILABLE) Boolean`,
  `@Named(PLAYBACK_STATUS_DESTINATION) Any?`) and the matching
  `PLAYBACK_STATUS_*` companion constants. `PlaybackStatusClicked`
  action handler replaces the nav-emit with `hatchet.w(...)` + a
  "not implemented yet" snackbar. `PlaybackStatusEntryPoint` /
  `PlaybackStatus` imports gone.
- `SettingsState`: `playbackStatusAvailable: Boolean` field gone;
  the conditional `playbackStatusRow` ListModel in the debug section
  removed; the `playbackStatusRow` private function deleted.
- `AndroidAppModule`: dropped both `@Named` playback-status
  `@Provides` bindings and the `PlaybackStatus` / `SettingsViewModel`
  / `PlaybackStatusEntryPoint` imports. Hilt-side LibrarySource +
  StringProvider + Hatchet + AppInfo bindings retained.

**The Metro-conversion attempt and what blocked it.** Tried converting
`SettingsViewModel` to `@Inject @ViewModelKey @ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())`
and switching `SettingsRoute` to `metroViewModel<SettingsViewModel>()`.
Metro's compiler plugin then hit:

```
e: org.jetbrains.kotlin.fir.pipeline.IrGenerationExtensionException:
'org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
$Companion.getIR_EXTERNAL_DECLARATION_STUB()'
Caused by: java.lang.NoSuchMethodError ...
at dev.zacsweers.metro.compiler.ir.IrKt.requireSimpleType(ir.kt:2178)
at dev.zacsweers.metro.compiler.ir.MetroIrPipeline.run(MetroIrPipeline.kt:40)
```

Per Metro's `compatibility.md`, Metro 1.1.1 supports Kotlin 2.3.20+ and
2.4.0+ explicitly; chipbox is on **Kotlin 2.3.10** (sage catalog),
which falls in a gap. The `@ContributesIntoMap` codepath specifically
triggers this — all of M4's plain `@ContributesTo` conversions don't
go through `requireSimpleType` and stay green.

Reverted the VM annotations + `SettingsRoute` accessor back to
`@HiltViewModel` / `hiltViewModel()`. The cleanup stands.

#### Sub-slice 5b — version-compat decision (pending)

Two routes to unblock VM conversions:

1. **Bump Kotlin to 2.3.20** in `sage/gradle/libs.versions.toml`.
   Side-effects: compose-compiler / KSP / kotlinx-serialization
   plugins pinned to Kotlin will need version bumps too. Most
   forward-compatible — keeps Metro on 1.1.1.
2. **Downgrade Metro to ~0.10.x** (the range that supports 2.3.10).
   Risk: `metrox-viewmodel-compose` may have different API shape
   pre-1.0; sub-slice 4b assumptions about
   `@ContributesTo`/`@ContributesIntoMap`/`binding<>()` may need
   adjustment.

Recommendation: option 1 — chipbox is already chasing recent stable
versions of every other framework (AGP 9, Compose 1.9, etc.); a
patch-level Kotlin bump is the smaller delta than rolling Metro back.

### Milestone 6 — drop Hilt (planned)

Final cleanup once everything Metro-side is green.

- Remove `dagger-hilt-android` Gradle plugin from `apps/android` and from
  `sage.di.android` convention plugin.
- Remove `hilt-android` / `hilt-compiler` / `hilt-navigation` /
  `hilt-navigation-compose` / `hilt-lifecycle-viewmodel-compose`
  dependencies.
- Delete `SageDiAndroidModulePlugin` (or repurpose for Metro-only setup if
  any modules need extra Metro config).
- Replace `@HiltAndroidApp` on `ChipboxApplication` with a plain
  `Application` subclass that owns the Metro graph.
- Replace `@AndroidEntryPoint` on `MainActivity` and `ChipboxPlaybackService`
  with hand-rolled injection: pull the graph from the
  `Application` cast on `onCreate` and read accessors directly.
- Drop the `kotlinx.coroutines.android.AsyncDispatcher` thing if anything
  was tied to Hilt's WorkManager helpers (audit).
- Final pass: `git grep -i hilt` should be empty.
- KMP-ify `features/settings/real` properly and close the M9 slice 5c gap.

## Risks / open questions

- **Metro maturity.** Metro is 1.x but actively developed. Production users
  exist (Slack's Circuit project uses it). Risk: hitting bugs we have to
  upstream-fix or work around. Mitigation: stay on the most recent stable
  release; have a Hilt-rollback escape hatch by NOT removing Hilt until
  Milestone 6.

- **Compose runtime stability annotations.** Hilt's `@HiltViewModel` is
  hard-coded as Compose-stable in some configurations. Metro's `@Inject` may
  not be without extra `compose-stability.conf` entries. Audit early.

- **MediaSessionService injection.** `ChipboxPlaybackService` is
  `@AndroidEntryPoint`. The replacement needs to pull the graph from the
  Application at `onCreate()` — straightforward but worth a smoke test
  early since the media-session lifecycle is one of the few non-VM things
  Hilt was driving.

- **Per-screen scoping fidelity.** `metrox-viewmodel-compose` routes through
  the AndroidX `ViewModelStore`, so Hilt's `NavBackStackEntry`-scoped
  clears-with-screen behavior is preserved on Android. Desktop side: verify
  in slice 1 whether the framework picks up Voyager's per-`Screen`
  `ViewModelStoreOwner` correctly, or whether we need a small bridge.

- **Build performance during the migration.** Both Metro and Hilt running
  on the same compile (Milestones 1–5) doubles annotation processing for
  affected modules. Should be a small absolute cost; measure after
  Milestone 2 lands.

## Out of scope

- Migrating Dagger's `@AssistedInject` / `@AssistedFactory` patterns (we
  don't use them today).
- Migrating to KotlinInject (the original alternative — rejected in favor of
  Metro because Metro has Dagger annotation interop and a single compiler
  plugin instead of KSP).
- The KMP migration's roadmap items 2 (real-time JVM audio), 3 (cross-platform
  native packaging), 4 (further commonMain hoists). Those are independent
  tracks tracked in `docs/kmp-migration.md`.
