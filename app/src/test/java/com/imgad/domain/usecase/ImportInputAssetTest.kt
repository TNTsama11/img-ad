package com.imgad.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportInputAssetTest {
    @Test
    fun thumbnailFailureDeletesCopiedInput() {
        val store = AssetUseCaseFakeFileStore(thumbnailFailure = IllegalStateException("thumbnail"))

        var thrown: Throwable? = null
        try {
            ImportInputAsset(store).invoke("content://input", "message")
        } catch (error: Throwable) {
            thrown = error
        }

        assertEquals("thumbnail", thrown?.message)
        assertTrue(store.deleted.contains("input-path"))
    }
}
