# `:cbox:common:scanner:fake`

> A no-op `Scanner` that counts `startScan()` calls and lets tests push state/events.

The `:fake` test double for `:cbox:common:scanner:api`. `CountingScanner`
subclasses the abstract `Scanner` with an empty `scan()` body that just bumps a
counter, and re-exposes the `protected` emit hooks so tests can drive
`state()` / `scanEvents()` collectors directly.

## Contents

| File | What it is |
| --- | --- |
| `CountingScanner.kt` | `Scanner` subclass: `scan()` increments `startCount`; `pushState` / `pushEvent` re-expose the base's `protected` `emitState`/`emitEvent`. |

## Why depend on this module

Use it in tests for anything that drives or observes a `Scanner` — e.g. a
settings or rescan-status view-model. Because production `Scanner.startScan()` is
`final` and launches `scan()` into its own scope, `startCount` is the only
observable signal that a rescan was triggered; `pushState`/`pushEvent` let the
test feed scan progress into the subject's collectors.

## Using it

```kotlin
val scanner = CountingScanner()            // dispatcher defaults to Dispatchers.Main
// ... action under test calls scanner.startScan() ...
assertEquals(1, scanner.startCount)

scanner.pushState(ScannerState.Scanning(gamesFound = 3))
scanner.pushEvent(ScannerEvent.FileScanned("track.nsf"))
```

Pair with `Dispatchers.setMain(UnconfinedTestDispatcher())` so `startScan()`'s
internal launch runs synchronously on the test thread.

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `:cbox:common:scanner:api`, `kotlinx-coroutines-core`
