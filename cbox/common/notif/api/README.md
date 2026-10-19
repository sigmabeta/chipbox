# `:cbox:common:notif:api`

> In-app notification types + the `NotifManager` contract.

The `:api` half of the notification system: the persisted `Notif` model, its category, the
`NotifState` snapshot, the manager interface the Home row and producers share, and the
`ScanFailureNotifRecorder` lifecycle contract. Pure multiplatform types — depend on this to read
notifications or add one; bind the production manager through `:cbox:common:notif:di`.

## Contents

| File | What it is |
| --- | --- |
| `Notif.kt` | `@Serializable` notification: stable `id`, localized `title` (`ChipboxStringId`), resolved `description`, `category`, and `isOneTime` (cleared on the next app launch). |
| `NotifCategory.kt` | `ERROR` / `INFO`; `ERROR` makes the Home card use the error-container colors. |
| `NotifState.kt` | `@Serializable` snapshot: active notifications keyed by id. |
| `NotifManager.kt` | `StateFlow<NotifState>` plus `addNotif` / `removeNotif`. |
| `ScanFailureNotifRecorder.kt` | `observe()` lifecycle contract for the app-lifetime scanner-failure producer. |

## Why depend on this module

Depend on `:api` from a producer that wants to post a notification
(`notifManager.addNotif(...)`), from the Home module that renders them, or from tests using
`:cbox:common:notif:fake`. The app wires `RealNotifManager` via `:di`.

## Using it

```kotlin
notifManager.addNotif(
    Notif(
        id = "my.notice".hashCode().toLong(),
        title = ChipboxStringId.NOTIF_SCAN_FAILED_TITLE,
        description = "…",
        category = NotifCategory.ERROR,
    ),
)
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `kotlin.serialization`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `:cbox:common:strings:api` (api), `kotlinx-coroutines-core` + `kotlinx-serialization-core` (api)
