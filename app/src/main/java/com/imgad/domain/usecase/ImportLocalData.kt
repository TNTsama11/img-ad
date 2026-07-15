package com.imgad.domain.usecase

import com.imgad.domain.port.ArchiveImportPreview
import com.imgad.domain.port.ArchiveImporter
import com.imgad.domain.port.ImportedArchive
import java.io.InputStream

interface LocalArchiveImporter {
    suspend fun apply(archive: ImportedArchive): ArchiveImportPreview
}

class ImportLocalData(private val store: ArchiveImporter, private val importer: LocalArchiveImporter? = null) {
    suspend fun preview(input: InputStream, password: CharArray? = null): ArchiveImportPreview {
        val archive = store.import(input, password)
        return ArchiveImportPreview(archive.data.providers.size, archive.data.sessions.size, archive.data.assets.size)
    }

    suspend fun apply(input: InputStream, password: CharArray? = null): ArchiveImportPreview {
        val archive = store.import(input, password)
        return importer?.apply(archive)
            ?: ArchiveImportPreview(archive.data.providers.size, archive.data.sessions.size, archive.data.assets.size)
    }
}
