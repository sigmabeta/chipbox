package net.sigmabeta.chipbox.artworkprovider.api

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.sigmabeta.chipbox.contentsource.LocalFileContentSource
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import java.io.File
import java.io.FileNotFoundException

class ArtworkProvider : ContentProvider() {

    // Read ArtworkUris.authority lazily (in onCreate, not as a field initializer) so it reflects
    // the applicationId-derived value set in ChipboxApplication.attachBaseContext — for a `.debug`
    // build that's net.sigmabeta.chipbox.debug.artworkprovider.api, matching the ${applicationId}
    // authority the manifest registers this provider under.
    private lateinit var matcher: UriMatcher

    @Volatile
    private var graph: ArtworkProviderGraph? = null

    override fun onCreate(): Boolean {
        matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(ArtworkUris.authority, "${ArtworkUris.SEGMENT_GAME}/#", MATCH_GAME)
            addURI(ArtworkUris.authority, "${ArtworkUris.SEGMENT_ARTIST}/#", MATCH_ARTIST)
        }
        return true
    }

    override fun getType(uri: Uri): String? = when (matcher.match(uri)) {
        MATCH_GAME, MATCH_ARTIST -> "image/*"
        else -> null
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") {
            throw SecurityException("ArtworkProvider is read-only (mode=$mode)")
        }

        val ctx = context ?: throw FileNotFoundException("No context")
        val ep = graph ?: resolveGraph(ctx).also { graph = it }

        val match = matcher.match(uri)
        val id = uri.lastPathSegment?.toLongOrNull()
            ?: throw FileNotFoundException("Bad id in $uri")

        val (kind, photoUrl) = when (match) {
            MATCH_GAME -> SEGMENT_GAME to runBlocking { loadGamePhoto(ep.repository(), id) }
            MATCH_ARTIST -> SEGMENT_ARTIST to runBlocking { loadArtistPhoto(ep.repository(), id) }
            else -> throw FileNotFoundException("Unsupported URI: $uri")
        }
        photoUrl ?: throw FileNotFoundException("No artwork for $uri")

        val cacheFile = ensureCached(ctx, ep.fileContentSource(), kind, id, photoUrl)
        return ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("ArtworkProvider is read-only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("ArtworkProvider is read-only")

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("ArtworkProvider is read-only")

    private fun resolveGraph(ctx: Context): ArtworkProviderGraph =
        ctx.applicationContext as? ArtworkProviderGraph
            ?: error(
                "Application ${ctx.applicationContext::class.java} does not implement " +
                    "ArtworkProviderGraph — the Metro graph cannot be reached from ArtworkProvider.",
            )

    private suspend fun loadGamePhoto(repo: Repository, id: Long): String? = repo.getGame(id)
            .filterIsInstance<Data.Succeeded<Game?>>()
            .first()
            .data
            ?.photoUrl

    private suspend fun loadArtistPhoto(repo: Repository, id: Long): String? = repo.getArtist(id)
            .filterIsInstance<Data.Succeeded<Artist?>>()
            .first()
            .data
            ?.photoUrl

    @Suppress("TooGenericExceptionCaught")
    private fun ensureCached(
        ctx: Context,
        source: LocalFileContentSource,
        kind: String,
        id: Long,
        photoUrl: String,
    ): File {
        val srcHash = photoUrl.hashCode().toUInt().toString(HEX_RADIX)
        val cacheDir = File(ctx.cacheDir, "artwork").apply { mkdirs() }
        val cacheFile = File(cacheDir, "${kind}_${id}_$srcHash.bin")

        if (cacheFile.exists() && cacheFile.length() > 0) {
            return cacheFile
        }

        val tmp = File.createTempFile("art_", ".tmp", cacheDir)
        try {
            val bytes = runBlocking { source.openBytes(photoUrl) }
                ?: throw FileNotFoundException("Cannot open source: $photoUrl")
            tmp.outputStream().use { output -> output.write(bytes) }
            if (!tmp.renameTo(cacheFile)) {
                tmp.copyTo(cacheFile, overwrite = true)
                tmp.delete()
            }
        } catch (t: Throwable) {
            tmp.delete()
            throw t
        }
        return cacheFile
    }

    companion object {
        private const val HEX_RADIX = 16
        private const val MATCH_GAME = 1
        private const val MATCH_ARTIST = 2
        private const val SEGMENT_GAME = ArtworkUris.SEGMENT_GAME
        private const val SEGMENT_ARTIST = ArtworkUris.SEGMENT_ARTIST
    }
}
