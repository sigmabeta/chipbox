# `:cbox:common:notif:fake`

> In-memory `NotifManager` for tests and the UI-test harness.

The `:fake` variant of the notification manager. `FakeNotifManager` keeps notifications in a
`MutableStateFlow` with no storage or coroutine scope, so unit tests and the cross-platform UI-test
graph can seed notices and assert removals directly.

## Contents

| File | What it is |
| --- | --- |
| `FakeNotifManager.kt` | `NotifManager` backed by an in-memory map; seed through the constructor or `addNotif`, then observe `notifState` / call `removeNotif`. |

## Why depend on this module

Tests that construct `NotifHomeModule` or `HomeViewModel` need a `NotifManager`; the UI-test
`TestAppGraph` binds this fake and exposes it as `fakeNotifManager` for specs to seed.

## Using it

```kotlin
val notifs = FakeNotifManager(initialNotifs = listOf(errorNotif))
val module = NotifHomeModule(notifs, stringProvider)
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `:cbox:common:notif:api`, `kotlinx-coroutines-core`
