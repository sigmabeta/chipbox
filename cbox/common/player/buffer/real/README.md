# `:cbox:common:player:buffer:real`

> `RealBufferManager` — a bounded, swappable two-channel pool backing the pipeline's buffer queue.

The `:real` implementation of `:cbox:common:player:buffer:api`. A single
`RealBufferManager` implements `ProducerBufferManager`, `ConsumerBufferManager`
and `BufferDebugSource` over two bounded coroutine `Channel`s. Depend on `:api`
for the interfaces; this module is the production binding, injected via
`:cbox:common:player:buffer:di`.

## Contents

| File | What it is |
| --- | --- |
| `RealBufferManager.kt` | The buffer manager. Holds a swappable `Pool` of two channels — `empty: Channel<ShortArray>` (producer borrows zeroed arrays) and `full: Channel<AudioBuffer>` (consumer drains envelopes). `setSampleRate` atomically rebuilds the pool sized to `BUFFER_LENGTH_MILLIS` (500 ms) of audio in `BUFFER_SIZE_BYTES_DEFAULT` (8192-byte) chunks. |

Implementation notes worth knowing before touching it:

- **Atomic pool swap.** The pool is a `MutableStateFlow<Pool?>`, so the `empty`/`full`
  pair is always published as one unit (no half-swapped pool) and a consumer parked
  on a null pool can *suspend* until `setSampleRate` rebuilds one instead of throwing.
- **Channel close as a wake signal.** `setSampleRate`/`reset` close the old channels
  *after* publishing the new pool; consumers wake on `ClosedReceiveChannelException`
  and re-read the new pool.
- **`reset()`** drops the pool and forgets the rate so the next `setSampleRate`
  rebuilds unconditionally — needed because the manager is a process singleton and a
  surviving-process cold start can otherwise leave a stale, drained pool.
- **`drain()`** must never suspend (it runs in the seek path): it snapshots one pool
  generation and uses non-blocking `trySend`, letting orphaned arrays fall to GC.
- `RealBufferManagerTest.kt` (`commonTest`) covers the pooling/swap/drain behaviour.

## Why depend on this module

Only `:cbox:common:player:buffer:di` depends on `:real` directly, to bind the single
`RealBufferManager` instance to all three `:api` interfaces in the `AppScope` graph.
Pipeline code depends on `:api`, not here. Tests that need a real (not faked) buffer
queue may depend on `:real`.

## Using it

```kotlin
// Constructed with a Hatchet logger; one instance serves all three roles.
val manager = RealBufferManager(hatchet)
val producer: ProducerBufferManager = manager
val consumer: ConsumerBufferManager = manager
val debug: BufferDebugSource = manager
```

In production this construction and the multi-interface binding happen in
`BufferModule` (`:di`), all `@SingleIn(AppScope::class)`.

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `chipbox.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (+ `commonTest`)
- **SAGE/module dependencies:** `:cbox:common:player:buffer:api` (`api`), `:cbox:common:player:common:api`, `sage.common.logging`
