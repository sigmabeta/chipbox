# `:cbox:common:utils:api`

> Shared low-level utilities: byte/number helpers, coroutine dispatchers, and RSN/RAR archive unpacking.

A leaf-ish utility module of small, broadly-shared helpers. Despite the `:api`
suffix it is mostly concrete code (with `expect`/`actual` declarations for the
platform-specific bits), not interfaces — it's the common toolbox that the
scanner, playback, and app layers reuse. It does **not** apply `sage.compose.kmp`
(no Compose).

## Contents

**Byte & number helpers** (`commonMain`)
- `ByteArrayExtensions.kt` — `ByteArray.convert()` (Latin-1 / ISO-8859-1 decode, by hand since the common stdlib has none) and `ByteArray.convertUtf()` (UTF-8).
- `NumberFormatting.kt` — `formatDecimal(value, decimals)`, a multiplatform stand-in for `String.format("%.Nf")` (round half-up).

**Coroutine dispatchers** (`expect`/`actual`)
- `EmulatorDispatcher.kt` — `expect val emulatorDispatcher`: a single dedicated thread every native-emulator call is confined to (the cores are global C state with no locking, so loads/renders/teardowns must never overlap). JVM/Android actual = one daemon thread (`src/main/java`); JS actual = `Dispatchers.Default` (single event loop already serialises).
- `IoDispatcher.kt` — `expect val ioDispatcher`: blocking-I/O dispatcher. JVM/Android actual = `Dispatchers.IO`; JS actual = `Dispatchers.Default` (no dedicated I/O pool).

**RAR / RSN archive handling** (`expect`/`actual`)
- `RarEntry.kt` — `class RarEntry(name, bytes)` and `expect fun unrar(bytes): List<RarEntry>?` (null when not a readable RAR / no decoder on this platform). JVM/Android actual (`Rar.kt`, `src/main/java`) decodes via **junrar** (JVM-only, RAR 4.x including solid archives, in iteration order); JS actual (`Rar.kt`, `jsMain`) is a null stub.
- `RsnArchive.kt` — RSN files are solid-RAR archives bundling a game's SPC rips. `rsnSpcMembers(bytes)` returns the `.spc` members in stable subsong order (case-insensitive by name), dropping non-SPC and directory entries. The single source of truth for member ordering, shared by the scanner's `RsnReader` and the playback staging layer. Constants `RSN_EXTENSION` / `RSN_MEMBER_EXTENSION`.

**App data dir** (JVM, `src/main/java`)
- `AppDataDir.kt` — `appDataDir(xdgName, nativeName)` resolves a JVM app's data directory per-OS (Windows `%LOCALAPPDATA%`, macOS `Library/Application Support`, Linux XDG `~/.local/share`). `resolveAppDataDir(...)` is the pure, testable core (all inputs are parameters).

## Why depend on this module

Depend on `:cbox:common:utils:api` when you need any of these primitives: the
emulator/IO dispatchers (playback + scanning code), RSN unpacking
(`rsnSpcMembers` in the scanner and playback staging), the byte/number decoders,
or the JVM app data dir (desktop/CLI apps). It is a leaf — no Chipbox/SAGE module
dependencies, only kotlinx-coroutines (common) and junrar (JVM).

## Using it

```kotlin
import net.sigmabeta.chipbox.utils.emulatorDispatcher
import net.sigmabeta.chipbox.utils.rsnSpcMembers

// Confine native-emulator work to the single emulator thread.
withContext(emulatorDispatcher) { emulator.loadTrack(...) }

// Unpack an RSN set into its ordered SPC subsongs (null = not decodable here).
val members = rsnSpcMembers(rsnBytes) ?: return
val spcAtIndex = members[subsong].bytes
```

```kotlin
// JVM apps: resolve the per-OS data directory.
val dir = appDataDir(xdgName = "chipbox", nativeName = "Chipbox")
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (helpers + `expect`), `src/main/java`
  (`jvmSharedMain` actuals: dispatchers, `unrar`, `AppDataDir`), `jsMain`
  (JS actuals / stubs)
- **SAGE/module dependencies:** none (leaf). External libs:
  `api(libs.kotlinx.coroutines.core)` (common); `chipbox.junrar` (`jvmSharedMain`)
