package com.imgad.domain

import com.imgad.domain.port.ArchiveSnapshot
import com.imgad.domain.port.ArchiveStore
import com.imgad.domain.port.ImportedArchive
import com.imgad.domain.usecase.ImportLocalData
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportLocalDataTest {
    @Test
    fun previewReturnsCountsWithoutApplyingRecords() = runBlocking {
        val store = object : ArchiveStore {
            override suspend fun export(snapshot: ArchiveSnapshot, output: java.io.OutputStream, password: CharArray?) = Unit
            override suspend fun import(input: java.io.InputStream, password: CharArray?): ImportedArchive =
                ImportedArchive(com.imgad.domain.port.ArchiveManifest(1, 1), com.imgad.domain.port.ArchiveData(), emptyMap())
        }
        val preview = ImportLocalData(store).preview(ByteArrayInputStream(ByteArray(0)))
        assertEquals(0, preview.providers)
        assertEquals(0, preview.assets)
    }
}
