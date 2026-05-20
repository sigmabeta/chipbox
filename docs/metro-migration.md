# Hilt → Metro migration — plan & status

Status: **Milestone 3 implemented.**

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

### Milestone 4 — bulk module sweep (planned)

Mechanical conversion of the remaining `@Module` files.

- For each of the 34 remaining `@Module` declarations:
  - `@InstallIn(SingletonComponent::class)` → `@ContributesTo(AppScope::class)`.
  - `@Module abstract class` → `@ContributesTo interface` (Metro doesn't
    distinguish).
  - `@Singleton` → `@SingleIn(AppScope::class)`.
  - `@Binds abstract fun` → Metro's `@Binds val Impl.bind: Iface` syntax,
    OR `@ContributesBinding(AppScope::class)` on the impl class directly
    (preferred for simple bind-to-interface cases — drops the module).
- A scripted bulk-rewrite (similar to `scripts/kmpify.py`) keeps the manual
  diff small.
- The JVM target's `JvmChipboxComponent` + `JvmModules.kt` get the same
  treatment, producing one shared module set that contributes to both
  the Android `ChipboxAppGraph` and a JVM-side `JvmChipboxGraph`.

### Milestone 5 — bulk VM sweep (planned)

Convert the remaining 14 `@HiltViewModel`-annotated classes using the
mechanism from Milestone 3. Each conversion is two annotation changes plus
verifying the route still gets a proper instance.

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
