# `:cbox:common:capabilities:api`

> App-level capability policy: which surfaces a host exposes.

A small, pure value type shared by the screens and every app graph. It answers questions like
"does this installation manage its own library?" without asking *which platform* is running.
Android, desktop, and the browser all render the same Compose screens; only the host's DI graph
decides what makes sense there.

## Contents

| File | What it is |
| --- | --- |
| `AppCapabilities.kt` | `AppCapabilities(canManageLibrary, showDebugSection, showErrorLog)`, all defaulting to `true` (the full desktop/Android surface). |

## Why depend on this module

Depend on it when a screen's state or action handling must vary by host policy (Settings' library
section, Home's empty-state CTAs, the shell's event sinks). The app graph binds one instance:

| Host | `canManageLibrary` | `showDebugSection` | `showErrorLog` |
| --- | --- | --- | --- |
| Android / desktop / UI tests | `true` | `true` | `true` |
| Browser (`apps/js`) | `false` | `false` | `true` |

The browser's library is owned by `chipbox-server`, so folder management, rescans, and clear-library
are unreachable there, and the debug section stays hidden even if the persisted five-tap toggle is
on. The Error Log remains reachable: it is browser-local and appears under Diagnostics.

Policy lives here, not in `expect`/`actual`, so it is injectable and testable in common code, and
adding a host-specific graying-out does not leak platform types into the screens.

## Using it

```kotlin
class SettingsViewModel @Inject constructor(
    private val capabilities: AppCapabilities,
    // …
) : ChipboxListViewModel<SettingsState>(SettingsState(), …)

// In the host graph:
@Provides @SingleIn(AppScope::class)
fun provideAppCapabilities(): AppCapabilities = AppCapabilities(canManageLibrary = false, …)
```

Screens copy the flags they need into their list state; the shell reads them from
`LocalAppCapabilities` to keep blocked destinations from being pushed.
