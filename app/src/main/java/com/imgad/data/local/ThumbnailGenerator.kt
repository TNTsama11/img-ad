package com.imgad.data.local

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class ThumbnailGenerator {
    fun generate(
        sourceProvider: () -> InputStream,
        destination: File,
        source: String,
        sourceMediaType: String,
    ) {
        mediaTypeExtension(sourceMediaType)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            sourceProvider().use { BitmapFactory.decodeStream(it, null, bounds) }
        } catch (error: IOException) {
            throw AssetStorageException.ImageDecodeFailed(source, error)
        } catch (error: IllegalArgumentException) {
            throw AssetStorageException.ImageDecodeFailed(source, error)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw AssetStorageException.ImageDecodeFailed(source)
        }

        val sample = calculateSample(bounds.outWidth, bounds.outHeight)
        val bitmap = try {
            BitmapFactory.Options().apply { inSampleSize = sample }.let { options ->
                sourceProvider().use { BitmapFactory.decodeStream(it, null, options) }
            } ?: throw AssetStorageException.ImageDecodeFailed(source)
        } catch (error: AssetStorageException) {
            throw error
        } catch (error: IOException) {
            throw AssetStorageException.ImageDecodeFailed(source, error)
        } catch (error: IllegalArgumentException) {
            throw AssetStorageException.ImageDecodeFailed(source, error)
        }

        val thumbnail = try {
            val longestSide = maxOf(bitmap.width, bitmap.height)
            if (longestSide > MAX_THUMBNAIL_SIDE) {
                val scale = MAX_THUMBNAIL_SIDE.toFloat() / longestSide
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).coerceAtLeast(1f).toInt(),
                    (bitmap.height * scale).coerceAtLeast(1f).toInt(),
                    true,
                )
            } else {
                bitmap
            }
        } catch (error: IllegalArgumentException) {
            bitmap.recycle()
            throw AssetStorageException.ImageDecodeFailed(source, error)
        }

        val temporary = destination.resolveSibling(".${destination.name}.${UUID.randomUUID()}.tmp")
        try {
            temporary.outputStream().use { output ->
                if (!thumbnail.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw AssetStorageException.ThumbnailWriteFailed(destination.path)
                }
            }
            if (!temporary.renameTo(destination)) {
                throw AssetStorageException.ThumbnailWriteFailed(destination.path)
            }
        } catch (error: AssetStorageException) {
            temporary.delete()
            destination.delete()
            throw error
        } catch (error: IOException) {
            temporary.delete()
            destination.delete()
            throw AssetStorageException.ThumbnailWriteFailed(destination.path, error)
        } finally {
            temporary.delete()
            if (thumbnail !== bitmap) thumbnail.recycle()
            bitmap.recycle()
        }
    }

    private fun calculateSample(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width / sample, height / sample) > MAX_DECODE_SIDE) sample *= 2
        return sample
    }

    private companion object {
        const val MAX_THUMBNAIL_SIDE = 512
        const val MAX_DECODE_SIDE = 1024
    }
}
