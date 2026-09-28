package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

class NativeDatabaseDriverFactory(private val databaseName: String = "hydrafit.db") :
    DatabaseDriverFactory {
    override fun createDriver(): SqlDriver =
        NativeSqliteDriver(HydraFitDatabase.Schema, databaseName)
}
