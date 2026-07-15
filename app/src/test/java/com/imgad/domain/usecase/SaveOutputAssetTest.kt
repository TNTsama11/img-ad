package com.imgad.domain.usecase

import com.imgad.domain.model.AssetSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveOutputAssetTest {
    @Test
    fun savesOutputAndMapsAssetSource() {
        val asset = SaveOutputAsset(AssetUseCaseFakeFileStore())
            .invoke(byteArrayOf(1, 2), "image/png", "message")

        assertEquals(AssetSource.OUTPUT, asset.source)
        assertEquals("input-path", asset.localUri)
        assertTrue(asset.id.isNotBlank())
    }
}
