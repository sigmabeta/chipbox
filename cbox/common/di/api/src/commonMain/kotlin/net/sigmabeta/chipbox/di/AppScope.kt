package net.sigmabeta.chipbox.di

/**
 * Marker class identifying the Chipbox application-wide DI scope — the Metro equivalent of
 * Hilt's `SingletonComponent`. Used in:
 *
 *  - `@DependencyGraph(AppScope::class)` on `ChipboxAppGraph` (Android) and the JVM-side
 *    graph: declares that the graph owns this scope's instances.
 *  - `@SingleIn(AppScope::class)` on `@Inject` classes or `@Provides` methods: marks the
 *    binding as a singleton within an `AppScope`-scoped graph (replaces `@Singleton`).
 *  - `@ContributesTo(AppScope::class)` on interfaces with `@Provides`/`@Binds` members:
 *    Anvil-style module aggregation (replaces Hilt's `@InstallIn(SingletonComponent::class)`).
 *  - `@ContributesBinding(AppScope::class)` on `@Inject` classes: binds the class to its
 *    declared interface within the scope (replaces `@Module abstract class { @Binds }`).
 *
 * Private constructor: this class is never instantiated — Metro uses the [KClass] reference
 * as a compile-time tag.
 */
class AppScope private constructor()
