package net.sigmabeta.chipbox.repository.mock

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.absoluteValue

class MockImageUrlGenerator @Inject constructor(
    @ApplicationContext val context: Context
) {
    val assetList by lazy {
        val list = context.assets.list("mock-images")
        list?.map { "file:///android_asset/mock-images/${it}" } ?: emptyList()
    }

    fun getImageUrl(seed: Int): String {
        val index = seed.absoluteValue % assetList.size
        return assetList[index]
    }
}