package net.sigmabeta.chipbox.ui.components.subs

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import net.sigmabeta.chipbox.ui.components.ImageNameListItem
import net.sigmabeta.chipbox.ui.components.previews.PreviewActionSink
import net.sigmabeta.chipbox.images.BitmapGenerator
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.ImageNameListModel
import net.sigmabeta.sage.images.SourceInfo
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.chipbox.ui.components.previews.ChipboxPreview
import net.sigmabeta.sage.ui.SageMaterialVectors
import net.sigmabeta.sage.ui.icons.CrossOutColor
import net.sigmabeta.sage.ui.icons.IcCrossOut24dp
import net.sigmabeta.sage.ui.vector

@Composable
fun CrossfadeImage(
    sourceInfo: SourceInfo,
    imagePlaceholder: Icon,
    contentDescription: String?,
    modifier: Modifier,
    forceGenBitmap: Boolean = LocalInspectionMode.current,
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
            forceGenBitmap -> FakeImage(current, Modifier.fillMaxSize())
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

    val context = LocalContext.current
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
    val context = LocalContext.current

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

@Composable
private fun FakeImage(
    sourceInfo: SourceInfo,
    modifier: Modifier
) {
    val bitmap = BitmapGenerator.generateBitmap(sourceInfo.info.toString())
    Image(
        painter = BitmapPainter(
            // Kotlin compiler complains without .toString() here....
            image = bitmap,
            filterQuality = FilterQuality.None
        ),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

@Preview
@Composable
private fun Light() {
    ChipboxPreview {
        Sample()
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Dark() {
    ChipboxPreview {
        Sample()
    }
}

@Composable
@Suppress("LongMethod", "MagicNumber")
private fun Sample() {
    Column(
        modifier = Modifier.background(
            color = MaterialTheme.colorScheme.background
        )
    ) {
        ImageNameListItem(
            ImageNameListModel(
                1234L,
                "Carrying the Weight of Life",
                SourceInfo(info = null),
                Icon.Description,
                null,
                clickAction = SageAction.Noop,
            ),
            PreviewActionSink { },
            Modifier,
            PaddingValues(horizontal = 8.dp)
        )

        Row {
            ElevatedRoundRect(
                modifier = Modifier
                    .size(64.dp)
                    .padding(8.dp),
                cornerRadius = 4.dp
            ) {
                CrossfadeImage(
                    sourceInfo = SourceInfo("etc"),
                    imagePlaceholder = Icon.Person,
                    contentDescription = null,
                    simulateError = true,
                    forceGenBitmap = false,
                    modifier = Modifier,
                )
            }

            ElevatedRoundRect(
                modifier = Modifier
                    .size(64.dp)
                    .padding(8.dp),
                cornerRadius = 4.dp
            ) {
                CrossfadeImage(
                    sourceInfo = SourceInfo(null),
                    imagePlaceholder = Icon.Description,
                    contentDescription = null,
                    modifier = Modifier,
                )
            }

            ElevatedRoundRect(
                modifier = Modifier
                    .size(64.dp)
                    .padding(8.dp),
                cornerRadius = 4.dp
            ) {
                CrossfadeImage(
                    sourceInfo = SourceInfo("doesn't matter"),
                    imagePlaceholder = Icon.Description,
                    contentDescription = null,
                    modifier = Modifier,
                )
            }
        }

        Row {
            ElevatedCircle(
                Modifier.size(64.dp)
            ) {
                CrossfadeImage(
                    sourceInfo = SourceInfo("etc"),
                    imagePlaceholder = Icon.Person,
                    contentDescription = null,
                    simulateError = true,
                    forceGenBitmap = false,
                    modifier = Modifier,
                )
            }

            ElevatedCircle(
                Modifier.size(64.dp)
            ) {
                CrossfadeImage(
                    sourceInfo = SourceInfo(null),
                    imagePlaceholder = Icon.Description,
                    contentDescription = null,
                    modifier = Modifier,
                )
            }

            ElevatedCircle(
                Modifier.size(64.dp)
            ) {
                CrossfadeImage(
                    sourceInfo = SourceInfo("doesn't matter"),
                    imagePlaceholder = Icon.Description,
                    contentDescription = null,
                    modifier = Modifier,
                )
            }
        }
    }
}
