package com.imgad.domain.usecase

import com.imgad.domain.port.ArchiveSnapshot
import com.imgad.domain.port.ArchiveExporter
import java.io.OutputStream

class ExportLocalData(private val store: ArchiveExporter) {
    suspend operator fun invoke(snapshot: ArchiveSnapshot, output: OutputStream, password: CharArray? = null) =
        store.export(snapshot, output, password)
}
