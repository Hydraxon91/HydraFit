package com.hydrafit.app.core.userdata.equipment

import com.hydrafit.app.core.domain.engine.PersonalRecord
import kotlinx.coroutines.flow.Flow

/** The user's manually entered best sets, used to seed the suggested-weight baseline. */
interface PersonalRecordRepository {
    fun observe(): Flow<List<PersonalRecord>>

    suspend fun set(record: PersonalRecord)

    suspend fun clear(exerciseId: String)
}
