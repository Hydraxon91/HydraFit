package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import com.hydrafit.app.core.domain.setup.requirePreferredWeekdays
import com.hydrafit.app.core.domain.time.DayOfWeek
import com.hydrafit.app.core.userdata.settings.PreferredWeekdaysRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SqlDelightPreferredWeekdaysRepository(private val database: HydraFitDatabase) :
    PreferredWeekdaysRepository {
    override suspend fun current(): Set<DayOfWeek>? =
        decodePreferredWeekdays(database.setupQueries.selectWeekdays().executeAsOne().weekdays)

    override fun observe(): Flow<Set<DayOfWeek>?> = database.setupQueries.selectWeekdays()
        .asFlow().mapToOne(Dispatchers.Default).map { decodePreferredWeekdays(it.weekdays) }

    override suspend fun set(weekdays: Set<DayOfWeek>?) {
        requirePreferredWeekdays(weekdays)
        database.transaction {
            database.savePreferredWeekdays(weekdays)
        }
    }
}

/** Call inside the transaction that owns the user's save action. */
internal fun HydraFitDatabase.savePreferredWeekdays(weekdays: Set<DayOfWeek>?) {
    requirePreferredWeekdays(weekdays)
    setupQueries.updateWeekdays(encodePreferredWeekdays(weekdays))
    if (weekdays != null) {
        plannerEngineQueries.insertIgnoreRow("DETERMINISTIC")
        plannerEngineQueries.updateDaysPerWeek(weekdays.size.toLong())
    }
}

internal fun encodePreferredWeekdays(weekdays: Set<DayOfWeek>?): String? =
    weekdays?.sortedBy { it.ordinal }?.joinToString(",") { it.name }

internal fun decodePreferredWeekdays(value: String?): Set<DayOfWeek>? {
    if (value == null) return null
    val values = value.split(',').map(DayOfWeek::valueOf)
    require(values.distinct().size == values.size)
    return values.toSet().also(::requirePreferredWeekdays)
}
