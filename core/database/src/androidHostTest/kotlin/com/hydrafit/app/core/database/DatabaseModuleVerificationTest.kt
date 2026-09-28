package com.hydrafit.app.core.database

import org.koin.test.verify.verify
import kotlin.test.Test

class DatabaseModuleVerificationTest {

    @Test
    fun databaseModuleDependenciesAreResolvable() {
        databaseModule.verify(extraTypes = listOf(DatabaseDriverFactory::class))
    }
}
