package com.imgad.domain.port

interface FileStore {
    fun copyInput(uri: String, messageId: String? = null): StoredAssetFile

    fun writeOutput(bytes: ByteArray, mediaType: String, messageId: String? = null): StoredAssetFile

    fun createThumbnail(uri: String, messageId: String? = null): StoredAssetFile

    fun delete(path: String)

    fun deleteForMessage(messageId: String)
}

data class StoredAssetFile(
    val localUri: String,
    val mediaType: String,
    val width: Int? = null,
    val height: Int? = null,
    val byteSize: Long,
)
