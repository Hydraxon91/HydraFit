package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.time.TimeProvider
import kotlin.test.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class DatabaseModuleVerificationTest {

    @Test
    fun databaseModuleDependenciesAreResolvable() {
        databaseModule.verify(
            extraTypes = listOf(DatabaseDriverFactory::class, TimeProvider::class)
        )
    }
}
