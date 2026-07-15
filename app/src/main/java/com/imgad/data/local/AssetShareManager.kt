package com.imgad.data.local

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.imgad.domain.model.Asset
import java.io.File

class AssetShareManager(context: Context) {
    private val appContext = context.applicationContext
    private val assetRoot = File(appContext.filesDir, ASSET_DIRECTORY).canonicalFile

    fun createShareIntent(asset: Asset): Intent {
        val file = asset.localUri.toPrivateAssetFile()
        val contentUri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.files",
            file,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = asset.mediaType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            clipData = ClipData.newRawUri("ImgAd image", contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun String.toPrivateAssetFile(): File {
        val uri = Uri.parse(this)
        val candidate = when (uri.scheme) {
            null -> File(this)
            "file" -> File(requireNotNull(uri.path))
            else -> throw SecurityException("Only private asset files can be shared")
        }.canonicalFile
        if (!candidate.toPath().startsWith(assetRoot.toPath()) || !candidate.isFile) {
            throw SecurityException("Asset is outside the private store")
        }
        return candidate
    }

    private companion object {
        const val ASSET_DIRECTORY = "imgad-assets"
    }
}
