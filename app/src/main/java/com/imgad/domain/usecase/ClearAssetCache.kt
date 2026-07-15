package com.imgad.domain.usecase

import com.imgad.domain.port.StorageCleaner
import com.imgad.domain.port.StorageUsage

class ClearAssetCache(private val cleaner: StorageCleaner) {
    operator fun invoke(): StorageUsage = cleaner.clearUnused()
}
