package com.imgad.data.local

import com.imgad.domain.port.StorageCleaner
import com.imgad.domain.port.StorageUsage
import com.imgad.domain.port.StorageUsageReader
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Paths

class PrivateAssetStorage(
    private val root: File,
    private val referencedPaths: (() -> Set<String>)? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val gracePeriodMillis: Long = DEFAULT_GRACE_PERIOD_MILLIS,
) : StorageUsageReader, StorageCleaner {
    override fun readUsage(): StorageUsage = files().fold(StorageUsage(0, 0)) { total, file ->
        StorageUsage(total.bytes + file.length(), total.files + 1)
    }

    override fun clearUnused(): StorageUsage {
        val referenced = referencedPaths?.invoke().orEmpty().mapNotNull(::canonicalPath).toSet()
        val cutoff = clock() - gracePeriodMillis
        files().filter { file ->
            val temporary = file.name.endsWith(".tmp") || file.name.startsWith(".")
            file.lastModified() <= cutoff && (temporary || (referencedPaths != null && file.canonicalPath !in referenced))
        }.forEach { file ->
            if (file.exists() && !file.delete()) throw IOException("Unable to delete unused asset: ${file.path}")
        }
        return readUsage()
    }

    private fun files(): List<File> = if (!root.exists()) emptyList() else root.walkTopDown().filter(File::isFile).toList()

    private fun canonicalPath(path: String): String? = runCatching {
        val uri = runCatching { URI(path) }.getOrNull()
        val file = if (uri?.scheme.equals("file", ignoreCase = true)) Paths.get(requireNotNull(uri)).toFile() else File(path)
        file.canonicalPath
    }.getOrNull()

    private companion object {
        const val DEFAULT_GRACE_PERIOD_MILLIS = 60 * 60 * 1000L
    }
}
