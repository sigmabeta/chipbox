package net.sigmabeta.chipbox.common.ui.components.api.subs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.ConstraintsSizeResolver
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Dimension
import coil3.size.Size as CoilSize
import coil3.size.SizeResolver
import net.sigmabeta.sage.components.GridImageSize
import net.sigmabeta.sage.images.SourceInfo
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.SageMaterialVectors
import net.sigmabeta.sage.ui.icons.CrossOutColor
import net.sigmabeta.sage.ui.icons.IcCrossOut24dp
import net.sigmabeta.sage.ui.vector
import androidx.compose.runtime.getValue

@Composable
fun CrossfadeImage(
    sourceInfo: SourceInfo,
    imagePlaceholder: Icon,
    contentDescription: String?,
    modifier: Modifier,
    // In a preview / Paparazzi render (LocalInspectionMode = true) Coil can't fetch, so render a
    // deterministic generated gradient via FakeImage instead of the loading/error placeholder.
    forceGenBitmap: Boolean = LocalInspectionMode.current,
    simulateError: Boolean = false,
    onImageLoadedChange: ((Boolean) -> Unit)? = null,
    // Hero / showcase surfaces (HeroImage) want the source-resolution bitmap so the upscale
    // into a large display rect is filtered from real pixels, not from a sub-sampled
    // approximation. Setting this true drops the ConstraintsSizeResolver and lets
    // AsyncImagePainter fall back to SizeResolver.ORIGINAL.
    loadOriginalSize: Boolean = false,
) {
    // AnimatedContent gives each `sourceInfo` its own composition scope so the outgoing
    // branch can keep rendering the previous painter (and its loaded image) while the
    // incoming branch starts a fresh Coil load. Without this, a re-keyed request mutates the
    // same AsyncImagePainter and we lose the old frame the moment the source changes.
    //
    // [crossfadeImagesEnabled] is false on JS — the AnimatedContent slot footprint compounds
    // with Kotlin/JS's emulated `Long` ops during grid scroll. JS skips the wrapper entirely
    // and renders the active branch directly; source-change transitions become hard cuts.
    if (crossfadeImagesEnabled) {
        AnimatedContent(
            targetState = sourceInfo,
            contentKey = { it.info },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = modifier,
            label = "CrossfadeImage.source",
        ) { current ->
            sourceBranch(
                current = current,
                imagePlaceholder = imagePlaceholder,
                contentDescription = contentDescription,
                forceGenBitmap = forceGenBitmap,
                simulateError = simulateError,
                onImageLoadedChange = onImageLoadedChange,
                loadOriginalSize = loadOriginalSize,
            )
        }
    } else {
        Box(modifier = modifier) {
            sourceBranch(
                current = sourceInfo,
                imagePlaceholder = imagePlaceholder,
                contentDescription = contentDescription,
                forceGenBitmap = forceGenBitmap,
                simulateError = simulateError,
                onImageLoadedChange = onImageLoadedChange,
                loadOriginalSize = loadOriginalSize,
            )
        }
    }
}

/** Pulled out so both the animated + plain branches above share the dispatch logic. */
@Composable
private fun sourceBranch(
    current: SourceInfo,
    imagePlaceholder: Icon,
    contentDescription: String?,
    forceGenBitmap: Boolean,
    simulateError: Boolean,
    onImageLoadedChange: ((Boolean) -> Unit)?,
    loadOriginalSize: Boolean,
) {
    when {
        current.info == null -> PlaceHolderImage(imagePlaceholder, Modifier.fillMaxSize())

        forceGenBitmap -> FakeImage(current, Modifier.fillMaxSize())

        else -> RealImage(
            sourceInfo = current,
            imagePlaceholder = imagePlaceholder,
            contentDescription = contentDescription,
            simulateError = simulateError,
            onImageLoadedChange = onImageLoadedChange,
            modifier = Modifier.fillMaxSize(),
            loadOriginalSize = loadOriginalSize,
        )
    }
}

@Composable
private fun RealImage(
    sourceInfo: SourceInfo,
    imagePlaceholder: Icon,
    contentDescription: String?,
    simulateError: Boolean,
    onImageLoadedChange: ((Boolean) -> Unit)?,
    modifier: Modifier,
    loadOriginalSize: Boolean,
) {
    if (simulateError) {
        ErrorImage(imagePlaceholder, contentDescription, modifier)
        return
    }

    val context = LocalPlatformContext.current
    // Without an explicit SizeResolver, rememberAsyncImagePainter falls back to
    // SizeResolver.ORIGINAL inside AsyncImagePainter.updateRequest and decodes every
    // cover at its source resolution — a 4K cover for a 64dp list thumb ends up as a
    // multi-MB native bitmap. ConstraintsSizeResolver is the same resolver AsyncImage
    // uses internally: applied as a Modifier it records the layout constraints during
    // measurement and feeds them to the request, so Coil samples to the displayed size.
    val sizeResolver = remember { ConstraintsSizeResolver() }
    // Snap the request to one of the GridImageSize buckets — cells with slightly different
    // measured pixels (different densities, column counts, paddings) collapse to the same
    // Coil memory-cache key instead of pinning a separate decoded bitmap each. Each bucket
    // is a square max-dim target; Coil's Scale.FILL + INEXACT then decides what gets
    // decoded against the source aspect.
    val bucketedSizeResolver = remember(sizeResolver) {
        SizeResolver { bucketRequestSize(sizeResolver.size()) }
    }
    val request = remember(context, sourceInfo.info, bucketedSizeResolver, loadOriginalSize) {
        val builder = ImageRequest.Builder(context).data(sourceInfo.info)
        if (!loadOriginalSize) builder.size(bucketedSizeResolver)
        builder.build()
    }
    val asyncPainter = rememberAsyncImagePainter(
        model = request,
        contentScale = ContentScale.Crop,
        // Default for the painter is FilterQuality.Low (bilinear). When the decoded
        // bitmap doesn't match the cell pixel-for-pixel — e.g. Crop on a landscape
        // source into a portrait cell, or any case where Precision.INEXACT lets the
        // decoder return a slightly off size — bilinear can look soft. Medium picks up
        // mipmap-aware sampling where the backend supports it without the per-frame
        // bicubic cost of High.
        filterQuality = FilterQuality.Medium,
    )

    // Remeasure → reload, but only when the bucket changes. ConstraintsSizeResolver
    // updates its latestConstraints on every measure pass; AsyncImagePainter only consumes
    // sizeResolver.size() once per launchJob() execution. Calling painter.restart() pushes
    // the painter back through Empty → Loading → Success — during Loading the Crossfade
    // shows the dark placeholder, which is what you see as a black flicker when a window
    // resize keeps the cell in the same bucket. Restart only when the bucket flips so
    // the cached bitmap stays painted while the cell's measured size drifts within a
    // bucket. (Skip entirely for original-size since pixel-size changes can't change
    // what gets loaded.)
    var measuredSize by remember(sourceInfo.info) { mutableStateOf(IntSize.Zero) }
    var lastBucket by remember(sourceInfo.info) { mutableStateOf<GridImageSize?>(null) }
    LaunchedEffect(measuredSize, loadOriginalSize) {
        if (loadOriginalSize || measuredSize == IntSize.Zero) return@LaunchedEffect
        val newBucket = bucketFor(CoilSize(measuredSize.width, measuredSize.height)) ?: return@LaunchedEffect
        val previousBucket = lastBucket
        lastBucket = newBucket
        if (previousBucket != null && previousBucket != newBucket) {
            asyncPainter.restart()
        }
    }

    val sizingModifier = if (loadOriginalSize) {
        modifier
    } else {
        modifier.then(sizeResolver).onSizeChanged { measuredSize = it }
    }

    RealStandardImage(
        asyncPainter,
        sourceInfo,
        imagePlaceholder,
        contentDescription,
        onImageLoadedChange,
        sizingModifier,
    )
}

@Composable
fun RealStandardImage(
    asyncPainter: AsyncImagePainter,
    sourceInfo: SourceInfo,
    imagePlaceholder: Icon,
    contentDescription: String?,
    onImageLoadedChange: ((Boolean) -> Unit)?,
    modifier: Modifier,
) {
    val state by asyncPainter.state.collectAsState()
    val isLoaded = state is AsyncImagePainter.State.Success
    // rememberUpdatedState so the callback can be a fresh lambda each recomposition
    // without re-firing the LaunchedEffect — if the outer AnimatedContent / Crossfade keeps
    // this branch alive during a transition we don't want to re-report a stale loaded=true.
    val latestCallback by rememberUpdatedState(onImageLoadedChange)
    LaunchedEffect(isLoaded) {
        latestCallback?.invoke(isLoaded)
    }

    // Coil's engine only calls Target.onStart (which transitions us to State.Loading) when
    // the memory cache MISSES; a hit skips the engine pipeline and goes Empty → Success
    // directly. Track whether we ever observed a Loading state so we can pick the right
    // crossfade behavior: a regular tween for actual fetches, and a snap for cache hits
    // so a scroll-back into a warm cell doesn't fade the placeholder in for 300ms before
    // showing the bitmap that was already in memory.
    //
    // We can't pre-check the memory cache from composition (Coil's MemoryCache.Key
    // includes size extras, and the size isn't resolved until layout), so we infer the
    // hit-vs-miss decision from the state trajectory instead.
    var sawLoading by remember(sourceInfo.info) { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state is AsyncImagePainter.State.Loading) {
            sawLoading = true
        }
    }
    val crossfadeSpec = if (sawLoading) tween<Float>() else snap()

    if (crossfadeImagesEnabled) {
        Crossfade(
            targetState = state,
            animationSpec = crossfadeSpec,
            label = "Image Crossfade",
            modifier = modifier,
        ) { loadingState ->
            stateBranch(loadingState, asyncPainter, imagePlaceholder, contentDescription)
        }
    } else {
        // JS: skip the Crossfade slot footprint, render the active state directly.
        Box(modifier = modifier) {
            stateBranch(state, asyncPainter, imagePlaceholder, contentDescription)
        }
    }
}

/** Pulled out so both the Crossfade'd + plain branches above share the state dispatch. */
@Composable
private fun stateBranch(
    loadingState: AsyncImagePainter.State,
    asyncPainter: AsyncImagePainter,
    imagePlaceholder: Icon,
    contentDescription: String?,
) {
    when (loadingState) {
        is AsyncImagePainter.State.Success -> Image(
            painter = asyncPainter,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        is AsyncImagePainter.State.Error -> ErrorImage(
            imagePlaceholder,
            contentDescription,
            Modifier.fillMaxSize(),
        )

        else -> PlaceHolderImage(imagePlaceholder, Modifier.fillMaxSize())
    }
}

@Composable
private fun PlaceHolderImage(
    imagePlaceholder: Icon,
    modifier: Modifier
) {
    Image(
        imageVector = imagePlaceholder.vector(),
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
    )
}

@Composable
private fun ErrorImage(
    imagePlaceholder: Icon,
    contentDescription: String?,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.errorContainer)
            .fillMaxSize()
            .padding(4.dp),
    ) {
        Image(
            imageVector = imagePlaceholder.vector(),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.error),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize(),
        )

        val errorContainer = MaterialTheme.colorScheme.errorContainer
        val errorBackground = MaterialTheme.colorScheme.error
        val crossOutVector = remember(errorBackground, errorContainer) {
            SageMaterialVectors.IcCrossOut24dp(
                mapOf(
                    CrossOutColor.Line to errorBackground,
                    CrossOutColor.Halo to errorContainer,
                )
            )
        }
        Icon(
            imageVector = crossOutVector,
            tint = Color.Unspecified,
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * The [GridImageSize] bucket a measured cell falls into, or `null` if both axes are
 * unbounded (Dimension.Undefined) — in which case Coil's own fallbacks resolve the
 * request to the source's original size.
 *
 * Uses the largest measured dimension to pick the smallest bucket that contains it; if
 * the measurement is larger than every bucket, the largest bucket is used.
 */
private fun bucketFor(measured: CoilSize): GridImageSize? {
    val w = (measured.width as? Dimension.Pixels)?.px ?: 0
    val h = (measured.height as? Dimension.Pixels)?.px ?: 0
    val maxDim = maxOf(w, h)
    if (maxDim <= 0) return null
    return GridImageSize.entries.firstOrNull { maxDim <= it.pixels }
        ?: GridImageSize.entries.last()
}

/**
 * Snap a measured size to one of the [GridImageSize] buckets so cells with slightly
 * different pixel measurements still share Coil memory-cache entries. Result is a square
 * ([pixels] × [pixels]); Coil's [coil3.size.Scale.FILL] + `Precision.INEXACT` then samples
 * the source bitmap to cover that square, preserving source aspect (so square buckets
 * don't force the decoded bitmap to be square).
 */
private fun bucketRequestSize(measured: CoilSize): CoilSize {
    val bucket = bucketFor(measured) ?: return measured
    return CoilSize(bucket.pixels, bucket.pixels)
}
