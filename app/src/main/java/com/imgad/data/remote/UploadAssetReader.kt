package com.imgad.data.remote

import com.imgad.domain.model.Asset
import com.imgad.domain.model.GenerationFailure
import com.imgad.domain.model.RemoteErrorKind
import com.imgad.domain.model.RemoteGenerationError
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaTypeOrNull

data class UploadAsset(
    val fileName: String,
    val mediaType: String,
    val bytes: ByteArray,
)

interface UploadAssetReader {
    fun read(asset: Asset): UploadAsset
}

class RejectingUploadAssetReader : UploadAssetReader {
    override fun read(asset: Asset): UploadAsset =
        throw GenerationFailure(RemoteGenerationError(RemoteErrorKind.CONFIG, "An asset reader must be configured"))
}

class RootedUploadAssetReader(
    private val assetRoot: File,
    private val maxBytes: Long = 25L * 1024L * 1024L,
) : UploadAssetReader {
    override fun read(asset: Asset): UploadAsset {
        val mediaType = asset.mediaType.lowercase().toMediaTypeOrNull()
        if (mediaType?.type != "image") {
            throw GenerationFailure(RemoteGenerationError(RemoteErrorKind.CONFIG, "Only image assets may be uploaded"))
        }
        val root = try {
            assetRoot.canonicalFile
        } catch (error: IOException) {
            throw GenerationFailure(RemoteGenerationError(RemoteErrorKind.CONFIG, "Invalid asset root"), error)
        }
        val file = try {
            File(asset.localUri).canonicalFile
        } catch (error: IOException) {
            throw GenerationFailure(RemoteGenerationError(RemoteErrorKind.CONFIG, "Invalid asset path"), error)
        }
        if (!file.path.startsWith(root.path + File.separator) || !file.isFile) {
            throw GenerationFailure(RemoteGenerationError(RemoteErrorKind.CONFIG, "Asset path is outside the configured root"))
        }
        if (file.length() > maxBytes) {
            throw GenerationFailure(RemoteGenerationError(RemoteErrorKind.RESPONSE_TOO_LARGE, "Upload asset is too large"))
        }
        return try {
            UploadAsset(file.name, mediaType.toString(), readLimited(file))
        } catch (error: IOException) {
            throw GenerationFailure(RemoteGenerationError(RemoteErrorKind.CONFIG, "Unable to read upload asset"), error)
        }
    }

    private fun readLimited(file: File): ByteArray {
        val output = ByteArrayOutputStream(minOf(file.length().toInt(), 8192))
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > maxBytes) {
                    throw GenerationFailure(RemoteGenerationError(RemoteErrorKind.RESPONSE_TOO_LARGE, "Upload asset is too large"))
                }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }
}
