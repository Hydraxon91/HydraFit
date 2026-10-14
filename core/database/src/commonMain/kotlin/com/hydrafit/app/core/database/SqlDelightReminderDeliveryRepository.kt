package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.schedule.ReminderDeliveryRepository

class SqlDelightReminderDeliveryRepository(private val database: HydraFitDatabase) :
    ReminderDeliveryRepository {
    private val queries = database.reminderDeliveryQueries

    override suspend fun isDelivered(activationId: Long, reminderEpochDay: Long): Boolean =
        queries.selectDeliveredAt(activationId, reminderEpochDay).executeAsOneOrNull() != null

    override suspend fun claim(
        activationId: Long,
        reminderEpochDay: Long,
        deliveredAtMillis: Long
    ): Boolean = database.transactionWithResult {
        val alreadyClaimed =
            queries.selectDeliveredAt(activationId, reminderEpochDay).executeAsOneOrNull() != null
        if (alreadyClaimed) {
            false
        } else {
            queries.markDelivered(activationId, reminderEpochDay, deliveredAtMillis)
            queries.deleteBefore(reminderEpochDay - RETAINED_DAYS)
            true
        }
    }

    override suspend fun release(activationId: Long, reminderEpochDay: Long) {
        queries.deleteDelivery(activationId, reminderEpochDay)
    }

    private companion object {
        /** Only today's row is ever read; a short history is kept for diagnostics before pruning. */
        const val RETAINED_DAYS = 7L
    }
}
