# `:cbox:common:scanner:api`

> The scanner contract — the abstract `Scanner` plus its state/event types.

The `:api` module for the library scanner: the abstract `Scanner` base class
and the `ScannerState` / `ScannerEvent` types it emits. Depend on it to consume
scan progress (drive a UI from it) or to subclass it; the concrete walk lives in
`:cbox:common:scanner:real`.

## Contents

| File | What it is |
| --- | --- |
| `Scanner.kt` | Abstract base. Subclasses implement `CoroutineScope.scan()`; the base provides final `startScan()` / `clearScan()` and replay-1 `state()` / `scanEvents()` flows, plus `protected` `emitState`/`emitEvent`/`isFailedAlready` for subclasses. Exposes a `version` (bump on a logic change that alters persisted tracks; stamped onto each track so the folder-skip check re-reads stale ones). |
| `RsnExtractor.kt` | Optional scanner collaborator for turning RSN archive members into standalone `.spc` files (`extract` + post-scan `prune`), plus `ExtractedRsnMember` and `DuplicateRsnContentException` (a second archive with identical bytes is refused while its owner exists). The `source` argument is the archive's stable identity across scans; implementations may persist it, and the server does (it writes a `.source` marker beside the output so the refusal survives an unchanged-folder skip). Null everywhere except the server graph, which keeps desktop/Android/CLI on archive staging. |
| `state/ScannerState.kt` | Sealed scan lifecycle: `Unknown`, `Idle`, `Scanning` (live running totals), `Complete`, `Failed(path, kind)` — `ScanFailureKind.FILE` names the unreadable file, `CONFIGURATION` names an admin-fixable root problem and has no failing file. |
| `state/ScannerEvent.kt` | Sealed per-item events: `FolderScanned` (per-folder progress heartbeat), `FileScanned` (per-file progress heartbeat), `GameFoundEvent`, `GameUpdated`, `GameRemoved`, `Unknown`. |

## Why depend on this module

Depend on `:api` for the `Scanner` type and its state/event streams — e.g. a
settings or rescan-status view-model collects `scanner.state()` and
`scanner.scanEvents()` to render progress. The app wires the concrete
`RealScanner` (from `:real`) into `AppScope`; tests substitute `CountingScanner`
(from `:fake`).

## Using it

```kotlin
class RescanStatusViewModel(scanner: Scanner) {
    val state: Flow<ScannerState> = scanner.state()
    val events: Flow<ScannerEvent> = scanner.scanEvents()

    fun rescan() = scanner.startScan()
}
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `:cbox:common:models:api`, `:cbox:common:utils:api`, `kotlinx-coroutines-core`, `sage.common.logging`
