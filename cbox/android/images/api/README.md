# `:cbox:android:images:api`

> Android image plumbing — placeholder bitmap generation, Coil decoder/fetcher, and a Hatchet-backed Coil logger.

Android system glue (`:api`): platform image utilities around the Android `Bitmap` and Coil 3,
plus one common piece (`HatchetCoilLogger`) shared with the JS app. The module is KMP
(`sage.kmp` + gated JS) but most code is Android-only: the common source set holds only the
logger, the JS app reuses it, and the Android `Bitmap`/Coil classes live in `androidMain` /
`src/main/java`.

## Contents

| File | What it is |
| --- | --- |
| `HatchetCoilLogger.kt` (`commonMain`) | Bridges Coil's `Logger` into SAGE `Hatchet` + `Analytics`. `minLevel = Warn` (Coil's verbose per-request spam would flood JS during grid scroll); errors are forwarded to `Analytics.logError`. Stays in `commonMain` (no `android.util.Log`) so JS can reuse it. |
| `BitmapGenerator.kt` (`androidMain`) | Deterministic placeholder-art generator: seeds a `Random` from a source object's hash and paints a gradient `ImageBitmap`. |
| `FakeOtherImageDecoder.kt` (`androidMain`) | Coil `Decoder` that renders a `BitmapGenerator` placeholder for non-PDF sources. |
| `FakeOtherImageFetcher.kt` (`src/main/java`/`jvmSharedMain`) | Coil `Fetcher<Uri>` returning a `java.io.File`-backed `SourceFetchResult`. |

## Why depend on this module

Depend on it for the Coil logger (`HatchetCoilLogger`, used by both the Android and JS image
loaders) and for the Android placeholder-art generation / fake Coil components used while real
artwork loads. `coil.kt.core` is re-exported via `api`.

## Using it

```kotlin
// Wire Coil to log through Hatchet:
ImageLoader.Builder(context)
    .logger(HatchetCoilLogger(hatchet, analytics))
    .build()

// Deterministic placeholder for a model:
val placeholder: ImageBitmap = BitmapGenerator.generateBitmap(game)
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `metro` (the `metro` compiler plugin and `sage.common.di` are added to `commonMain` directly because `sage.di`'s plugin is Android/JVM-only)
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain` (logger), `androidMain` (Bitmap/Coil), `src/main/java`/`jvmSharedMain` (`java.io.File` fetcher)
- **SAGE/module dependencies:** `coil.kt.core`/`compose`/`okhttp`, `sage.common.di`, `sage.common.analytics`, `sage.common.logging`, `sage.common.images`
