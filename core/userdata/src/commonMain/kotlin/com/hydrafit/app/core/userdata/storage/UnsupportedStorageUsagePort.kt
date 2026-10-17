package com.hydrafit.app.core.userdata.storage

import com.hydrafit.app.core.domain.storage.StorageUsagePort

/** Platforms with no reportable storage facts: zero database bytes and unknown folder usage. */
class UnsupportedStorageUsagePort : StorageUsagePort {
    override suspend fun databaseBytes(): Long = 0L

    override suspend fun automaticBackupBytes(): Long? = null

    override suspend fun automaticBackupFileCount(): Int? = null
}
