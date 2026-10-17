package com.hydrafit.app.core.domain.storage

/** Byte-size and folder-usage facts the storage screen reports. */
interface StorageUsagePort {
    /** The app's database file size in bytes; 0 when the platform cannot report it. */
    suspend fun databaseBytes(): Long

    /** Bytes used by this app's recorded automatic snapshots, or null when unknown/unsupported. */
    suspend fun automaticBackupBytes(): Long?

    /** Number of this app's recorded automatic snapshots, or null when unsupported. */
    suspend fun automaticBackupFileCount(): Int?
}

/** Reads the scoped categories' counts and removes one category in one transaction. */
interface DataManagementRepository {
    /** Every supported count, read in one transaction. Never mutates. */
    suspend fun counts(): Map<DataCountKind, Int>

    /**
     * Removes [category]'s data in one transaction; [DataCategory.ALL_DATA] clears every user-owned
     * row and returns the install to onboarding. The seeded catalog, credentials, model files and
     * automatic snapshots are never touched.
     */
    suspend fun clear(category: DataCategory)
}
