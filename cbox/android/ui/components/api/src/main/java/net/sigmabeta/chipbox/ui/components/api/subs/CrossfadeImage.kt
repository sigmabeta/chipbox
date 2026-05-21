package net.sigmabeta.chipbox.ui.components.api.subs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
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
    simulateError: Boolean = false,
    onImageLoadedChange: ((Boolean) -> Unit)? = null,
) {
    // AnimatedContent gives each `sourceInfo` its own composition scope so the
    // outgoing branch can keep rendering the previous painter (and its loaded
    // image) while the incoming branch starts a fresh Coil load. Without this,
    // a re-keyed request mutates the same AsyncImagePainter and we lose the
    // old frame the moment the source changes.
    AnimatedContent(
        targetState = sourceInfo,
        contentKey = { it.info },
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        modifier = modifier,
        label = "CrossfadeImage.source",
    ) { current ->
        when {
            current.info == null -> PlaceHolderImage(imagePlaceholder, Modifier.fillMaxSize())

            else -> RealImage(
                sourceInfo = current,
                imagePlaceholder = imagePlaceholder,
                contentDescription = contentDescription,
                simulateError = simulateError,
                onImageLoadedChange = onImageLoadedChange,
                modifier = Modifier.fillMaxSize(),
            )
        }
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
) {
    if (simulateError) {
        ErrorImage(imagePlaceholder, contentDescription, modifier)
        return
    }

    val context = LocalPlatformContext.current
    val request = remember(context, sourceInfo.info) {
        ImageRequest.Builder(context).data(sourceInfo.info).build()
    }
    val asyncPainter = rememberAsyncImagePainter(model = request)

    RealStandardImage(
        asyncPainter,
        sourceInfo,
        imagePlaceholder,
        contentDescription,
        onImageLoadedChange,
        modifier,
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
    val context = LocalPlatformContext.current

    // If Coil already has this image in memory, the painter will resolve in
    // one frame from cache — skip the Crossfade and just paint over the
    // placeholder so we don't flash the loading icon on scroll re-entry.
    val cacheHit = remember(sourceInfo.info) {
        sourceInfo.info?.let { key ->
            SingletonImageLoader.get(context)
                .memoryCache
                ?.get(MemoryCache.Key(key.toString())) != null
        } ?: false
    }

    val state by asyncPainter.state.collectAsState()
    val isLoaded = state is AsyncImagePainter.State.Success
    // rememberUpdatedState so the callback can be a fresh lambda each recomposition
    // without re-firing the LaunchedEffect — if the outer AnimatedContent / Crossfade keeps
    // this branch alive during a transition we don't want to re-report a stale loaded=true.
    val latestCallback by rememberUpdatedState(onImageLoadedChange)
    LaunchedEffect(isLoaded) {
        latestCallback?.invoke(isLoaded)
    }

    if (cacheHit) {
        Box(modifier = modifier) {
            PlaceHolderImage(imagePlaceholder, Modifier.fillMaxSize())
            if (state is AsyncImagePainter.State.Success) {
                Image(
                    painter = asyncPainter,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        return
    }

    Crossfade(
        targetState = state,
        label = "Image Crossfade",
    ) { loadingState ->
        when (loadingState) {
            is AsyncImagePainter.State.Success -> Image(
                painter = asyncPainter,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = modifier,
            )

            is AsyncImagePainter.State.Error -> ErrorImage(
                imagePlaceholder,
                contentDescription,
                modifier
            )

            else -> PlaceHolderImage(imagePlaceholder, modifier)
        }
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
