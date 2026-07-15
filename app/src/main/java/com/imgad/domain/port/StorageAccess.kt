package com.imgad.domain.port

data class StorageUsage(val bytes: Long, val files: Int)

interface StorageUsageReader {
    fun readUsage(): StorageUsage
}

interface StorageCleaner {
    fun clearUnused(): StorageUsage
}
