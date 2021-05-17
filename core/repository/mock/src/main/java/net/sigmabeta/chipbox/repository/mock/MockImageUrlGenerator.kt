package net.sigmabeta.chipbox.repository.mock

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.absoluteValue

class MockImageUrlGenerator @Inject constructor(
    @ApplicationContext val context: Context
) {
    private val gameImageList by lazy {
        val list = context.assets.list(SUBPATH_GAMES)
        list?.map { "$FULLPATH_GAMES/$it" } ?: emptyList()
    }

    private val artistImageList by lazy {
        val list = context.assets.list(SUBPATH_ARTISTS)
        list?.map { "$FULLPATH_ARTISTS/$it" } ?: emptyList()
    }

    fun getGameImageUrl(seed: Int): String {
        val index = seed.absoluteValue % gameImageList.size
        return gameImageList[index]
    }

    fun getArtistImageUrl(seed: Int): String {
        val index = seed.absoluteValue % artistImageList.size
        return artistImageList[index]
    }
    
    companion object {
        const val SCHEMA_ANDROID_ASSET = "file:///android_asset"
        const val FOLDER_MOCK_IMAGES = "mock-images"
        const val SUBFOLDER_GAMES = "games"
        const val SUBFOLDER_ARTISTS = "artists"
        
        const val SUBPATH_GAMES = "$FOLDER_MOCK_IMAGES/$SUBFOLDER_GAMES"
        const val SUBPATH_ARTISTS = "$FOLDER_MOCK_IMAGES/$SUBFOLDER_ARTISTS"
        
        const val FULLPATH_GAMES = "$SCHEMA_ANDROID_ASSET/$SUBPATH_GAMES"
        const val FULLPATH_ARTISTS = "$SCHEMA_ANDROID_ASSET/$SUBPATH_ARTISTS"
    }
}