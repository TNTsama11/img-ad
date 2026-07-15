package com.imgad.data.local

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.OutputStream
import java.util.UUID

interface MediaStoreGateway {
    fun insert(values: ContentValues): Uri

    fun openOutputStream(uri: Uri): OutputStream?

    fun update(uri: Uri, values: ContentValues): Int

    fun delete(uri: Uri): Int
}

class MediaStoreSaver(private val gateway: MediaStoreGateway) {
    constructor(context: Context) : this(ContentResolverMediaStoreGateway(context.applicationContext.contentResolver))

    constructor(contentResolver: ContentResolver) : this(ContentResolverMediaStoreGateway(contentResolver))

    fun save(bytes: ByteArray, mediaType: String): Uri {
        val extension = mediaStoreExtension(mediaType)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${UUID.randomUUID()}$extension")
            put(MediaStore.Images.Media.MIME_TYPE, mediaType)
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/ImgAd",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = gateway.insert(values)
        try {
            gateway.openOutputStream(uri)?.use { output -> output.write(bytes) }
                ?: error("Unable to open MediaStore output")
            val committed = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            check(gateway.update(uri, committed) > 0) { "Unable to commit MediaStore output" }
            return uri
        } catch (error: Exception) {
            val cleanupFailure = try {
                if (gateway.delete(uri) <= 0) {
                    IllegalStateException("Unable to delete pending MediaStore row")
                } else {
                    null
                }
            } catch (cleanupError: Exception) {
                cleanupError
            }
            cleanupFailure?.let(error::addSuppressed)
            throw error
        }
    }
}

private fun mediaStoreExtension(mediaType: String): String = when (mediaType.lowercase()) {
    "image/png" -> ".png"
    "image/jpeg" -> ".jpg"
    "image/webp" -> ".webp"
    else -> throw AssetStorageException.UnsupportedMediaType(mediaType)
}

private class ContentResolverMediaStoreGateway(
    private val contentResolver: ContentResolver,
) : MediaStoreGateway {
    override fun insert(values: ContentValues): Uri = requireNotNull(
        contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values),
    ) { "Unable to insert MediaStore image" }

    override fun openOutputStream(uri: Uri): OutputStream? = contentResolver.openOutputStream(uri)

    override fun update(uri: Uri, values: ContentValues): Int = contentResolver.update(uri, values, null, null)

    override fun delete(uri: Uri): Int = contentResolver.delete(uri, null, null)
}
