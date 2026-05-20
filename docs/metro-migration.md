# Hilt → Metro migration — plan & status

Status: **Not started.** Plan only.

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

### Milestone 1 — foundation smoke test (planned)

The "is this viable" round. Lands Metro on a small KMP-ready module first to
prove the toolchain works before sweeping the rest of the codebase.

- **Add `dev.zacsweers.metro` to the version catalog.** Pin to 1.1.1 or
  whatever the current stable is. Add the `metro` plugin alias, plus library
  aliases for `metrox-viewmodel` and `metrox-viewmodel-compose` (the plugin
  auto-adds the core runtime; the metrox extensions need explicit deps).
- **Pick one module — `features/settings/real`** — as the smoke test.
  - Flip to `sage.kmp + sage.compose.kmp` (the blocker that triggered this
    whole migration).
  - Apply `id("dev.zacsweers.metro")` in addition to the KMP plugins.
  - Enable `metro { interop { includeDagger() } }` so existing
    `@Inject` / `@HiltViewModel` annotations on `SettingsViewModel` still
    type-check.
  - Move `SettingsViewModel.kt` to jvmSharedMain (still has `@HiltViewModel`
    via Dagger interop — Metro accepts the annotation but does nothing with
    it). The Android build's apps-level Hilt processor still picks it up
    via the compile classpath.
  - Move `SettingsRoute.kt` to androidMain (Android-only SAF activity-result
    API).
  - Verify `:apps:android:assembleDebug` still green — Hilt's existing wiring
    keeps working on Android, Metro just sits there in the same module.
- **Define `AppScope` + `@SingleIn`**. New file in
  `cbox/common/di/api` (new sage.kmp module) — a marker class
  `class AppScope private constructor()` and the convenience scope marker for
  graph-scoped singletons. Used in slice 2.

This slice doesn't change any runtime behavior; it proves that the Metro
plugin can be applied to a sage.kmp module without fighting Hilt's existing
processing, and that Metro's Dagger interop accepts our current annotation
usage.

### Milestone 2 — `ChipboxAppGraph` (planned)

Build the Metro equivalent of `SingletonComponent`. Doesn't remove Hilt yet
— both DI systems run side-by-side in this slice.

- **`ChipboxAppGraph`** as `@DependencyGraph(AppScope::class)` in apps/android.
  Initial accessors mirror the Hilt-injected services `MainActivity` /
  `ChipboxPlaybackService` need today.
- **`ChipboxApplication`** (the `@HiltAndroidApp` class today) gains a Metro
  `appGraph: ChipboxAppGraph` property created via `createGraph<ChipboxAppGraph>()`.
- **`AndroidAppModule`** (the single apps/android-level Hilt module) converts:
  - `@Module` interface → `@ContributesTo(AppScope::class)` interface.
  - `@Provides` companion methods → top-level `@Provides` in the same file.
  - `@Singleton` → `@SingleIn(AppScope::class)`.
- Verify both the Hilt graph and the Metro graph build and a smoke test
  (one new property accessed via `appGraph.appInfo` in `MainActivity`)
  resolves correctly.

### Milestone 3 — wire `metrox-viewmodel-compose` (planned)

What would have been the hard slice — designing a `@HiltViewModel` replacement
— is mostly a config job thanks to `metrox-viewmodel-compose`. Slice goals:

- **Extend `ChipboxAppGraph` (and the JVM-side graph) with `ViewModelGraph`**
  so the framework's three multibindings (`viewModelProviders`,
  `assistedFactoryProviders`, `manualAssistedFactoryProviders`) are exposed.
- **Build one `MetroViewModelFactory` per target** and provide it via
  `LocalMetroViewModelFactory` at the root composable in both `MainActivity`
  (Android) and `DesktopMain` (JVM).
- **Convert one VM end-to-end** (probably `SettingsViewModel`):
  - Drop `@HiltViewModel`.
  - Add `@Inject @ViewModelKey @ContributesIntoMap(AppScope::class)`.
  - Switch the route composable's `hiltViewModel<SettingsViewModel>()` (Android)
    and the chipbox-side `chipboxViewModel<SettingsViewModel>()` (JVM) to
    `metroViewModel<SettingsViewModel>()`. Confirm per-screen scoping still
    behaves correctly on both targets.
- **Delete the chipbox-side VM provider** —
  `cbox/common/ui/vm/api/.../ViewModelProvider.kt`,
  `ChipboxViewModelComposable.kt`, the Android
  `AndroidHiltViewModelProvider`, the JVM `JvmViewModelProvider`, and the
  `LocalViewModelProvider` CompositionLocal all go. `metroViewModel<T>()` is
  the canonical accessor going forward.
- This slice closes the original KMP-migration M9 slice 5c gap — the JVM
  target instantiates `SettingsViewModel` through the same Metro graph as
  Android does.

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
