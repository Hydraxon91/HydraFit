package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.schedule.ActivationEntry
import com.hydrafit.app.core.domain.schedule.ActivationStatus
import com.hydrafit.app.core.domain.schedule.ActivationWorkout
import com.hydrafit.app.core.domain.schedule.OccurrenceEntry
import com.hydrafit.app.core.domain.schedule.OccurrenceStatus
import com.hydrafit.app.core.domain.schedule.ScheduleMode
import com.hydrafit.app.core.domain.schedule.TrainingActivation
import com.hydrafit.app.core.domain.schedule.WorkoutOccurrence
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleRepository
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleState
import com.hydrafit.app.core.domain.settings.TrainingGuidancePreferenceRepository
import com.hydrafit.app.core.domain.settings.TrainingGuidancePreferences
import com.hydrafit.app.core.domain.startup.StartupReadiness
import com.hydrafit.app.core.domain.time.BootIdentityProvider
import com.hydrafit.app.core.domain.time.DayOfWeek
import com.hydrafit.app.core.domain.workout.PersistedRestCountdown
import com.hydrafit.app.core.domain.workout.RestAlertScheduler
import com.hydrafit.app.core.domain.workout.RestCountdownRepository
import com.hydrafit.app.core.domain.workout.WorkoutSession
import com.hydrafit.app.core.domain.workout.WorkoutSessionRepository
import com.hydrafit.app.core.userdata.settings.GuidedWorkoutPreferenceRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * R077-06: a live countdown keeps its one deadline job across unrelated emissions, including a
 * due-but-not-executed job, so a foreground completion is not dropped. Relevant invalidation and
 * disabling both presentation modes still cancel; an already expired restore never replays.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RestAlertCoordinatorTest {
    private val dispatcher = StandardTestDispatcher()

    private fun runCoordinatorTest(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        block()
    }

    @Test
    fun unrelatedPreferenceChangeKeepsDueForegroundCompletion() = runCoordinatorTest {
        val stored = countdown(deadline = 10_000L)
        val countdowns = FakeRestCountdownRepository(stored)
        val preferences = FakeTrainingGuidancePreferences().also {
            it.setRestCompletionSignalEnabled(true)
        }
        val guided = FakeGuidedWorkoutPreferenceRepository(true)
        val sessions = FakeWorkoutSessionRepository(session())
        val schedule = FakeScheduleRepository().also { it.set(activation(), occurrences(), 30L) }
        val alerts = RecordingAlerts()
        var elapsed = 1_000L
        val presentation = RestCompletionPresentation { elapsed }
        var signals = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            presentation.completions.collect { signals++ }
        }
        presentation.setVisible(true)
        val coordinator = coordinator(
            countdowns = countdowns,
            preferences = preferences,
            guided = guided,
            sessions = sessions,
            schedule = schedule,
            alerts = alerts,
            completion = presentation,
            elapsedNow = { elapsed }
        )
        try {
            coordinator.start()
            runCurrent()
            // The clock reaches the deadline, but the scheduled expiry work has not run yet.
            elapsed = 10_000L
            preferences.setShortRestCoachingEnabled(true)
            runCurrent()
            assertEquals(0, signals)
            advanceTimeBy(9_000L)
            runCurrent()
            assertEquals(1, signals)

            // A later unrelated emission cannot replay the handled completion.
            preferences.setShortRestCoachingEnabled(false)
            runCurrent()
            advanceTimeBy(1_000L)
            runCurrent()
            assertEquals(1, signals)
        } finally {
            coordinator.close()
            runCurrent()
        }
    }

    @Test
    fun disablingBothPresentationModesBeforeExpiryPreventsSignal() = runCoordinatorTest {
        val stored = countdown(deadline = 10_000L)
        val countdowns = FakeRestCountdownRepository(stored)
        val preferences = FakeTrainingGuidancePreferences().also {
            it.setRestCountdownEnabled(true)
            it.setRestCompletionSignalEnabled(true)
        }
        var elapsed = 1_000L
        val presentation = RestCompletionPresentation { elapsed }
        var signals = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            presentation.completions.collect { signals++ }
        }
        presentation.setVisible(true)
        val coordinator = coordinator(
            countdowns = countdowns,
            preferences = preferences,
            guided = FakeGuidedWorkoutPreferenceRepository(true),
            sessions = FakeWorkoutSessionRepository(session()),
            schedule = FakeScheduleRepository().also { it.set(activation(), occurrences(), 30L) },
            alerts = RecordingAlerts(),
            completion = presentation,
            elapsedNow = { elapsed }
        )
        try {
            coordinator.start()
            runCurrent()
            elapsed = 10_000L
            preferences.setRestCountdownEnabled(false)
            preferences.setRestCompletionSignalEnabled(false)
            runCurrent()
            advanceTimeBy(9_000L)
            runCurrent()
            assertEquals(0, signals)
        } finally {
            coordinator.close()
            runCurrent()
        }
    }

    @Test
    fun endingSessionBeforeExpiryPreventsSignal() = runCoordinatorTest {
        val stored = countdown(deadline = 10_000L)
        val sessions = FakeWorkoutSessionRepository(session())
        val countdowns = FakeRestCountdownRepository(stored)
        val preferences = FakeTrainingGuidancePreferences().also {
            it.setRestCountdownEnabled(true)
            it.setRestCompletionSignalEnabled(true)
        }
        var elapsed = 1_000L
        val presentation = RestCompletionPresentation { elapsed }
        var signals = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            presentation.completions.collect { signals++ }
        }
        presentation.setVisible(true)
        val coordinator = coordinator(
            countdowns = countdowns,
            preferences = preferences,
            guided = FakeGuidedWorkoutPreferenceRepository(true),
            sessions = sessions,
            schedule = FakeScheduleRepository().also { it.set(activation(), occurrences(), 30L) },
            alerts = RecordingAlerts(),
            completion = presentation,
            elapsedNow = { elapsed }
        )
        try {
            coordinator.start()
            runCurrent()
            elapsed = 10_000L
            sessions.end("session", 0L)
            runCurrent()
            advanceTimeBy(9_000L)
            runCurrent()
            assertEquals(0, signals)
        } finally {
            coordinator.close()
            runCurrent()
        }
    }

    @Test
    fun expiredRestoreAndEnableNeverReplayForeground() = runCoordinatorTest {
        val expired = countdown(deadline = 5_000L)
        val countdowns = FakeRestCountdownRepository(expired)
        val preferences = FakeTrainingGuidancePreferences().also {
            it.setRestCompletionSignalEnabled(true)
        }
        var elapsed = 10_000L
        val presentation = RestCompletionPresentation { elapsed }
        var signals = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            presentation.completions.collect { signals++ }
        }
        presentation.setVisible(true)
        val coordinator = coordinator(
            countdowns = countdowns,
            preferences = preferences,
            guided = FakeGuidedWorkoutPreferenceRepository(true),
            sessions = FakeWorkoutSessionRepository(session()),
            schedule = FakeScheduleRepository().also { it.set(activation(), occurrences(), 30L) },
            alerts = RecordingAlerts(),
            completion = presentation,
            elapsedNow = { elapsed }
        )
        try {
            coordinator.start()
            runCurrent()
            assertEquals(0, signals)
            // Toggling the opt-in and advancing time cannot revive an already expired countdown.
            preferences.setRestCompletionSignalEnabled(false)
            runCurrent()
            preferences.setRestCompletionSignalEnabled(true)
            runCurrent()
            advanceTimeBy(1_000L)
            runCurrent()
            assertEquals(0, signals)
        } finally {
            coordinator.close()
            runCurrent()
        }
    }

    private fun TestScope.coordinator(
        countdowns: RestCountdownRepository,
        preferences: TrainingGuidancePreferenceRepository,
        guided: GuidedWorkoutPreferenceRepository,
        sessions: WorkoutSessionRepository,
        schedule: WorkoutScheduleRepository,
        alerts: RestAlertScheduler,
        completion: RestCompletionPresentation,
        elapsedNow: () -> Long
    ) = RestAlertCoordinator(
        countdowns = countdowns,
        preferences = preferences,
        guided = guided,
        sessions = sessions,
        schedule = schedule,
        boot = BootIdentityProvider { "boot" },
        readiness = object : StartupReadiness {
            override val isReady = MutableStateFlow(true)
        },
        alerts = alerts,
        dispatcher = StandardTestDispatcher(testScheduler),
        elapsedNow = elapsedNow,
        completion = completion
    )

    private fun countdown(deadline: Long) = PersistedRestCountdown(
        deadlineElapsedMillis = deadline,
        durationMillis = 120_000L,
        bootIdentity = "boot",
        sessionId = "session",
        occurrenceId = 30L,
        exerciseId = "back-squat"
    )

    private fun session() = WorkoutSession("session", 0L, localEpochDay = 0L, occurrenceId = 30L)

    private fun activation() = TrainingActivation(
        id = 5L,
        name = "Block",
        createdAtMillis = 0L,
        startEpochDay = 0L,
        mode = ScheduleMode.WEEKDAY,
        weekdays = setOf(DayOfWeek.MONDAY),
        status = ActivationStatus.ACTIVE,
        workouts = listOf(
            ActivationWorkout(
                id = 9L,
                position = 0,
                name = "Upper",
                entries = listOf(
                    ActivationEntry(
                        id = 20L,
                        exerciseId = "back-squat",
                        exerciseName = "Back Squat",
                        movementPattern = MovementPattern.SQUAT,
                        sets = 2,
                        reps = 8,
                        weightKg = 100.0
                    )
                )
            )
        )
    )

    private fun occurrences() = listOf(
        WorkoutOccurrence(
            id = 30L,
            activationId = 5L,
            activationWorkoutId = 9L,
            queuePosition = 0,
            status = OccurrenceStatus.PENDING,
            entries = listOf(
                OccurrenceEntry(
                    id = 40L,
                    sourceActivationEntryId = 20L,
                    position = 0,
                    exerciseId = "back-squat",
                    exerciseName = "Back Squat",
                    movementPattern = MovementPattern.SQUAT,
                    sets = 2,
                    reps = 8,
                    weightKg = 100.0
                )
            )
        )
    )

    private class FakeRestCountdownRepository(countdown: PersistedRestCountdown?) :
        RestCountdownRepository {
        private val state = MutableStateFlow(countdown)

        override fun observe() = state

        override suspend fun load(): PersistedRestCountdown? = state.value

        override suspend fun save(countdown: PersistedRestCountdown) {
            state.value = countdown
        }

        override suspend fun clear() {
            state.value = null
        }
    }

    private class FakeTrainingGuidancePreferences : TrainingGuidancePreferenceRepository {
        private val state = MutableStateFlow(TrainingGuidancePreferences())

        override fun observe() = state

        override suspend fun get() = state.value

        override suspend fun setRestAlertsEnabled(enabled: Boolean) {
            state.value = state.value.copy(restAlertsEnabled = enabled)
        }

        override suspend fun setShortRestCoachingEnabled(enabled: Boolean) {
            state.value = state.value.copy(shortRestCoachingEnabled = enabled)
        }

        override suspend fun setRestCountdownEnabled(enabled: Boolean) {
            state.value = state.value.copy(restCountdownEnabled = enabled)
        }

        override suspend fun setRestCompletionSignalEnabled(enabled: Boolean) {
            state.value = state.value.copy(restCompletionSignalEnabled = enabled)
        }
    }

    private class FakeGuidedWorkoutPreferenceRepository(private val enabled: Boolean) :
        GuidedWorkoutPreferenceRepository {
        override suspend fun isGuidedWorkoutEnabled(): Boolean = enabled

        override fun guidedWorkoutFlow(): Flow<Boolean> = MutableStateFlow(enabled)

        override suspend fun setGuidedWorkoutEnabled(enabled: Boolean) = Unit
    }

    private class FakeWorkoutSessionRepository(initialSession: WorkoutSession?) :
        WorkoutSessionRepository {
        private val openSession = MutableStateFlow(initialSession)

        override suspend fun create(session: WorkoutSession) {
            openSession.value = session
        }

        override suspend fun end(id: String, endedAtMillis: Long) {
            if (openSession.value?.id == id) openSession.value = null
        }

        override suspend fun open(): WorkoutSession? = openSession.value

        override fun openFlow(): Flow<WorkoutSession?> = openSession

        override suspend fun all(): List<WorkoutSession> = emptyList()
    }

    private class RecordingAlerts : RestAlertScheduler {
        override suspend fun schedule(countdown: PersistedRestCountdown) = Unit

        override fun cancel() = Unit
    }

    /** Minimal in-memory active block with the single occurrence the coordinator validates. */
    private class FakeScheduleRepository : WorkoutScheduleRepository {
        private val state = MutableStateFlow(WorkoutScheduleState())
        private val occurrences = MutableStateFlow<List<WorkoutOccurrence>>(emptyList())

        fun set(
            activation: TrainingActivation,
            occurrences: List<WorkoutOccurrence>,
            selected: Long?
        ) {
            this.occurrences.value = occurrences
            state.value = WorkoutScheduleState(
                activeActivationId = activation.id,
                selectedOccurrenceId = selected,
                legacyFallbackEnabled = false
            )
        }

        override fun observeScheduleState(): Flow<WorkoutScheduleState> = state

        override fun observeActiveActivation(): Flow<TrainingActivation?> =
            MutableStateFlow<TrainingActivation?>(null)

        override fun observeOccurrences(activationId: Long): Flow<List<WorkoutOccurrence>> =
            occurrences.map { list -> list.filter { it.activationId == activationId } }

        override suspend fun scheduleState(): WorkoutScheduleState = state.value

        override suspend fun setScheduleState(state: WorkoutScheduleState) {
            this.state.value = state
        }

        override suspend fun activeActivation(): TrainingActivation? = null

        override suspend fun getActivation(id: Long): TrainingActivation? = null

        override suspend fun occurrences(activationId: Long): List<WorkoutOccurrence> = emptyList()

        override suspend fun getOccurrence(id: Long): WorkoutOccurrence? =
            occurrences.value.firstOrNull { it.id == id }

        override suspend fun acceptAndActivate(
            acceptedPlan: AcceptedPlan?,
            activation: TrainingActivation,
            scheduledEpochDays: List<Long?>,
            replaceActive: Boolean
        ): Long = 0L

        override suspend fun resolveOccurrence(
            occurrenceId: Long,
            expectedRevision: Int,
            status: OccurrenceStatus,
            resolvedAtMillis: Long,
            entries: List<OccurrenceEntry>
        ): WorkoutScheduleState = state.value

        override suspend fun updateActivationHeader(activation: TrainingActivation) = Unit

        override suspend fun updateOccurrence(occurrence: WorkoutOccurrence) = Unit

        override suspend fun replaceOccurrenceEntries(
            occurrenceId: Long,
            entries: List<OccurrenceEntry>
        ) = Unit

        override suspend fun deleteOccurrencesForActivation(activationId: Long) = Unit

        override suspend fun isTemplateReferenced(templateId: Long): Boolean = false
    }
}
