package net.sigmabeta.chipbox.utils

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Single dedicated thread that every native-emulator call is confined to.
 *
 * The emulators are process-wide singletons over global C state with no internal locking, so
 * `loadTrack` / `generateBuffer` / `teardown` must never overlap across threads — a teardown that
 * frees the core while another track's render-ahead writer is mid-`generateBuffer` is a
 * use-after-free that crashes the native core (seen as SIGSEGV in the GBA/USF cores during rapid
 * track skips). Routing all of them through this one dispatcher serialises them, which is also the
 * single-threaded access the [net.sigmabeta.chipbox.player.emulators.Emulator] contract already
 * assumes. A real OS thread (not just [CoroutineDispatcher.limitedParallelism]`(1)`) because some
 * cores keep thread-local state. JS has a single event-loop thread already, so no confinement.
 */
expect val emulatorDispatcher: CoroutineDispatcher
