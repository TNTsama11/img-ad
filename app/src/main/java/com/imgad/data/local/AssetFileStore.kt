package com.imgad.data.local

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.imgad.domain.port.FileStore as DomainFileStore
import com.imgad.domain.port.StoredAssetFile as DomainStoredAssetFile
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

typealias FileStore = DomainFileStore
typealias StoredAssetFile = DomainStoredAssetFile

sealed class AssetStorageException(message: String, cause: Throwable? = null) : IOException(message, cause) {
    class UnsupportedMediaType(val mediaType: String) :
        AssetStorageException("Unsupported media type: $mediaType")

    class ImageDecodeFailed(val source: String, cause: Throwable? = null) :
        AssetStorageException("Unable to decode image metadata: $source", cause)

    class InputUnavailable(val source: String, cause: Throwable? = null) :
        AssetStorageException("Unable to read input asset: $source", cause)

    class WriteFailed(val destination: String, cause: Throwable? = null) :
        AssetStorageException("Unable to write asset: $destination", cause)

    class ThumbnailWriteFailed(val destination: String, cause: Throwable? = null) :
        AssetStorageException("Unable to write thumbnail: $destination", cause)

    class InvalidPath(val path: String, cause: Throwable? = null) :
        AssetStorageException("Asset path is outside the private store: $path", cause)
}

class AssetFileStore(
    private val rootDirectory: File,
    private val inputStreamProvider: (String) -> InputStream,
    private val mediaTypeProvider: (String) -> String?,
) : FileStore {
    constructor(context: Context) : this(
        rootDirectory = File(context.applicationContext.filesDir, ASSET_DIRECTORY),
        inputStreamProvider = { source ->
            val uri = Uri.parse(source)
            if (uri.scheme == null || uri.scheme == "file") {
                FileInputStream(uri.path ?: source)
            } else {
                context.applicationContext.contentResolver.openInputStream(uri)
                    ?: throw AssetStorageException.InputUnavailable(source)
            }
        },
        mediaTypeProvider = { source ->
            val uri = Uri.parse(source)
            context.applicationContext.contentResolver.getType(uri) ?: guessMediaType(source)
        },
    )

    override fun copyInput(uri: String, messageId: String?): StoredAssetFile {
        val mediaType = mediaTypeProvider(uri) ?: DEFAULT_BINARY_MEDIA_TYPE
        val destination = scopedDirectory(messageId, INPUT_DIRECTORY)
            .resolve("${UUID.randomUUID()}${mediaTypeExtension(mediaType)}")
        val temporary = temporarySibling(destination)
        destination.parentFile?.mkdirs()
        try {
            inputStreamProvider(uri).use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            commit(temporary, destination)
            return metadata(destination, uri, mediaType)
        } catch (error: AssetStorageException) {
            temporary.delete()
            destination.delete()
            throw error
        } catch (error: IOException) {
            temporary.delete()
            destination.delete()
            throw AssetStorageException.InputUnavailable(uri, error)
        } finally {
            temporary.delete()
        }
    }

    override fun writeOutput(bytes: ByteArray, mediaType: String, messageId: String?): StoredAssetFile {
        val extension = outputMediaTypeExtension(mediaType)
        val destination = scopedDirectory(messageId, OUTPUT_DIRECTORY)
            .resolve("${UUID.randomUUID()}$extension")
        val temporary = temporarySibling(destination)
        destination.parentFile?.mkdirs()
        try {
            temporary.writeBytes(bytes)
            commit(temporary, destination)
            return metadata(destination, destination.path, mediaType)
        } catch (error: AssetStorageException) {
            temporary.delete()
            destination.delete()
            throw error
        } catch (error: IOException) {
            temporary.delete()
            destination.delete()
            throw AssetStorageException.WriteFailed(destination.path, error)
        } finally {
            temporary.delete()
        }
    }

    override fun createThumbnail(uri: String, messageId: String?): StoredAssetFile {
        val mediaType = mediaTypeProvider(uri) ?: guessMediaType(uri)
            ?: throw AssetStorageException.UnsupportedMediaType("unknown")
        outputMediaTypeExtension(mediaType)
        val destination = scopedDirectory(messageId, THUMBNAIL_DIRECTORY)
            .resolve("${UUID.randomUUID()}.png")
        destination.parentFile?.mkdirs()
        return try {
            ThumbnailGenerator().generate(
                sourceProvider = { inputStreamProvider(uri) },
                destination = destination,
                source = uri,
                sourceMediaType = mediaType,
            )
            val dimensions = readImageDimensions(destination, destination.path)
            StoredAssetFile(
                localUri = destination.path,
                mediaType = PNG_MEDIA_TYPE,
                width = dimensions.first,
                height = dimensions.second,
                byteSize = destination.length(),
            )
        } catch (error: AssetStorageException) {
            destination.delete()
            throw error
        } catch (error: IOException) {
            destination.delete()
            throw AssetStorageException.ImageDecodeFailed(uri, error)
        }
    }

    override fun delete(path: String) {
        val root: File
        val target: File
        try {
            root = rootDirectory.canonicalFile
            target = File(path).canonicalFile
        } catch (error: IOException) {
            throw AssetStorageException.InvalidPath(path, error)
        }
        val rootPath = root.path + File.separator
        if (!target.path.startsWith(rootPath)) {
            throw AssetStorageException.InvalidPath(path)
        }
        if (target.exists() && !target.deleteRecursively()) {
            throw AssetStorageException.WriteFailed(path)
        }
    }

    override fun deleteForMessage(messageId: String) {
        val directory = scopedDirectory(messageId)
        if (directory.exists() && !directory.deleteRecursively()) {
            throw AssetStorageException.WriteFailed(directory.path)
        }
    }

    private fun metadata(file: File, source: String, mediaType: String): StoredAssetFile {
        val dimensions = if (mediaType.startsWith("image/")) {
            readImageDimensions(file, source)
        } else {
            null
        }
        return StoredAssetFile(
            localUri = file.path,
            mediaType = mediaType,
            width = dimensions?.first,
            height = dimensions?.second,
            byteSize = file.length(),
        )
    }

    private fun readImageDimensions(file: File, source: String): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            BitmapFactory.decodeFile(file.path, options)
        } catch (error: IllegalArgumentException) {
            throw AssetStorageException.ImageDecodeFailed(source, error)
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw AssetStorageException.ImageDecodeFailed(source)
        }
        return options.outWidth to options.outHeight
    }

    private fun scopedDirectory(messageId: String?, child: String? = null): File {
        val base = if (messageId == null) rootDirectory else rootDirectory.resolve(
            "messages/${safeMessageDirectory(messageId)}",
        )
        return if (child == null) base else base.resolve(child)
    }

    private fun temporarySibling(destination: File): File =
        destination.resolveSibling(".${destination.name}.${UUID.randomUUID()}.tmp")

    private fun commit(temporary: File, destination: File) {
        if (!temporary.renameTo(destination)) {
            throw AssetStorageException.WriteFailed(destination.path)
        }
    }

    private fun safeMessageDirectory(messageId: String): String = MessageDigest.getInstance("SHA-256")
        .digest(messageId.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    companion object {
        private const val ASSET_DIRECTORY = "imgad-assets"
        private const val INPUT_DIRECTORY = "inputs"
        private const val OUTPUT_DIRECTORY = "outputs"
        private const val THUMBNAIL_DIRECTORY = "thumbnails"
        private const val DEFAULT_BINARY_MEDIA_TYPE = "application/octet-stream"
        private const val PNG_MEDIA_TYPE = "image/png"

        internal fun guessMediaType(source: String): String? = when {
            source.endsWith(".png", ignoreCase = true) -> "image/png"
            source.endsWith(".jpg", ignoreCase = true) || source.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            source.endsWith(".webp", ignoreCase = true) -> "image/webp"
            else -> null
        }
    }
}

internal fun mediaTypeExtension(mediaType: String): String = when (mediaType.lowercase()) {
    "image/png" -> ".png"
    "image/jpeg" -> ".jpg"
    "image/webp" -> ".webp"
    "text/plain" -> ".bin"
    "application/octet-stream" -> ".bin"
    else -> throw AssetStorageException.UnsupportedMediaType(mediaType)
}

private fun outputMediaTypeExtension(mediaType: String): String = when (mediaType.lowercase()) {
    "image/png", "image/jpeg", "image/webp" -> mediaTypeExtension(mediaType)
    else -> throw AssetStorageException.UnsupportedMediaType(mediaType)
}
