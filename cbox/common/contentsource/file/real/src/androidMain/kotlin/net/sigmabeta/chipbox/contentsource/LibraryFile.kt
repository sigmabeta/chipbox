package net.sigmabeta.chipbox.contentsource

import android.net.Uri

data class LibraryFile(
    val uri: Uri,
    val parentDocumentId: String,
    val name: String,
    val extension: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
)
