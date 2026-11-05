package com.hydrafit.app.feature.progress

import com.hydrafit.app.core.domain.routine.ClusterKind
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.ExerciseProgressCalculator
import com.hydrafit.app.core.domain.workout.ExerciseProgressRequest
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.ProgressDay
import com.hydrafit.app.core.domain.workout.ProgressRecord
import com.hydrafit.app.core.domain.workout.ProgressRecordKey
import com.hydrafit.app.core.domain.workout.ProgressRecordType
import com.hydrafit.app.core.domain.workout.WorkoutClusterSummary
import com.hydrafit.app.core.domain.workout.WorkoutSet
import com.hydrafit.app.core.userdata.presentation.PresentationNumberFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgressChartModelTest {

    @Test
    fun volumeAxisStartsAtZeroAndContainsTheObservedMaximumWithNiceTicks() {
        val axis = progressAxis(
            listOf(point(day = 0L, value = 200.0), point(day = 1L, value = 950.0)),
            ProgressMetric.EXTERNAL_VOLUME
        )

        assertEquals(0.0, axis.min)
        assertEquals(1000.0, axis.max, 1e-9)
        assertEquals(200.0, axis.step, 1e-9)
        assertEquals(6, axis.ticks.size)
        assertEquals(0.0, axis.ticks.first())
        assertEquals(1000.0, axis.ticks.last(), 1e-9)
        assertTrue(axis.ticks.all { it >= 0.0 })
    }

    @Test
    fun estimateAxisPadsTheObservedBoundsAndStillContainsEveryObservation() {
        val points = listOf(point(day = 0L, value = 100.0), point(day = 1L, value = 140.0))
        val axis = progressAxis(points, ProgressMetric.ESTIMATED_ONE_REP_MAX)

        // 5% of the 40-unit span pads the bounds to 98..142, then the nice step expands them.
        assertEquals(80.0, axis.min, 1e-9)
        assertEquals(160.0, axis.max, 1e-9)
        assertEquals(20.0, axis.step, 1e-9)
        assertTrue(axis.min <= 100.0 && axis.max >= 140.0)
        assertTrue(axis.ticks.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun flatAndSingleEstimatesUseAFixedPaddingAndNeverGoBelowZero() {
        val flat = progressAxis(
            listOf(point(day = 0L, value = 100.0)),
            ProgressMetric.ESTIMATED_ONE_REP_MAX
        )
        assertTrue(flat.min <= 100.0 && flat.max >= 100.0)
        // 5% of the 100-unit magnitude pads each side, then the nice bounds keep that width.
        assertEquals(10.0, flat.max - flat.min, 1e-9)
        assertTrue(flat.min >= 0.0)

        val nearZero = progressAxis(
            listOf(point(day = 0L, value = 0.5)),
            ProgressMetric.ESTIMATED_ONE_REP_MAX
        )
        assertEquals(0.0, nearZero.min)
        assertTrue(nearZero.max >= 0.5)
        assertTrue(nearZero.ticks.all { it >= 0.0 })

        // A flat value uses one display unit as its minimum padding, so the pound axis differs
        // from the kilogram axis even though both observations live in canonical kilograms.
        val kilograms = progressEstimateAxis(listOf(point(day = 0L, value = 1.0)))
        val pounds = progressEstimateAxis(
            listOf(point(day = 0L, value = 1.0)),
            WeightUnit.LB
        )
        assertEquals(0.0, kilograms.min)
        assertEquals(0.5, pounds.min, 1e-9)
        assertEquals(2.0, kilograms.max, 1e-9)
        assertEquals(1.5, pounds.max, 1e-9)
        assertTrue(pounds.min <= 1.0 && pounds.max >= 1.0)
    }

    @Test
    fun axisMappingKeepsMarksInsideThePlotAndClampsOutliers() {
        val axis = ProgressAxis(min = 80.0, max = 160.0, step = 20.0, ticks = emptyList())
        val radius = 4f
        val top = 10f
        val bottom = 110f

        assertEquals(top + radius, progressAxisY(160.0, axis, top, bottom, radius))
        assertEquals(bottom - radius, progressAxisY(80.0, axis, top, bottom, radius))
        assertEquals(
            top + radius + (bottom - top - 2f * radius) / 2f,
            progressAxisY(120.0, axis, top, bottom, radius)
        )
        assertEquals(bottom - radius, progressAxisY(10.0, axis, top, bottom, radius))
        assertEquals(top + radius, progressAxisY(500.0, axis, top, bottom, radius))
    }

    @Test
    fun chartMarkPositionsStayInsideTheReservedPlot() {
        val radius = 3f
        val left = 40f
        val right = 300f
        assertEquals(left + radius, progressChartMarkX(0L, 0L, 10L, left, right, radius))
        assertEquals(right - radius, progressChartMarkX(10L, 0L, 10L, left, right, radius))
        assertEquals(
            left + radius + (right - left - 2f * radius) / 2f,
            progressChartMarkX(5L, 0L, 10L, left, right, radius)
        )
    }

    @Test
    fun dateTicksKeepTheFirstAndLastDayAndRespectTheLabelBudget() {
        assertEquals(listOf(3L), progressDateTickDays(3L, 3L, maxLabels = 4))

        val shortWindow = progressDateTickDays(0L, 3L, maxLabels = 5)
        assertEquals(listOf(0L, 1L, 2L, 3L), shortWindow)

        val year = progressDateTickDays(0L, 364L, maxLabels = 4)
        assertEquals(4, year.size)
        assertEquals(0L, year.first())
        assertEquals(364L, year.last())
        assertTrue(year.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun normalPhoneDateLabelsUseFinalEndpointSpansAndDoNotOverlap() {
        val from = 0L
        val to = 27L
        val plotLeft = 40f
        val plotRight = 320f
        val radius = 3f
        val gap = 6f
        val labelWidth = 58f
        fun x(day: Long) = progressChartMarkX(day, from, to, plotLeft, plotRight, radius)

        // Legacy geometry from the failing build: count labels from the average width, then align
        // the first and last labels flush to their marks. The endpoint needs a full label width
        // toward its neighbour, so this representative phone geometry overlaps at both ends.
        val legacyCount = (((plotRight - plotLeft) / (labelWidth + gap)).toInt()).coerceIn(2, 6)
        val legacyDays = progressDateTickDays(from, to, legacyCount)
        val legacySeparated = legacyDays.mapIndexed { index, day ->
            val left = when {
                index == 0 -> x(day)
                index == legacyDays.lastIndex -> x(day) - labelWidth
                else -> x(day) - labelWidth / 2f
            }
            left to left + labelWidth
        }.zipWithNext().all { (earlier, later) -> later.first >= earlier.second + gap }
        assertFalse(legacySeparated)

        val placements = progressDateLabelPlacements(
            from = from,
            to = to,
            widthFor = { labelWidth },
            xFor = { day -> x(day) },
            plotLeft = plotLeft,
            plotRight = plotRight,
            gap = gap
        )

        assertEquals(from, placements.first().day)
        assertEquals(to, placements.last().day)
        assertEquals(placements.size, placements.map { it.day }.distinct().size)
        assertDateLabelsSeparated(placements, gap)
        assertTrue(
            placements.all { it.left >= plotLeft && it.right <= plotRight + 0.001f },
            "every date label must stay inside the plot: $placements"
        )
    }

    @Test
    fun narrowLargeFontPlotStacksTheDateEndpointsInsteadOfOverlapping() {
        val from = 0L
        val to = 27L
        val plotLeft = 40f
        val plotRight = 180f
        val radius = 3f
        val gap = 6f
        fun x(day: Long) = progressChartMarkX(day, from, to, plotLeft, plotRight, radius)

        // A 2x font on a narrow plot leaves no single row wide enough for both full ISO endpoints.
        val placements = progressDateLabelPlacements(
            from = from,
            to = to,
            widthFor = { 120f },
            xFor = { day -> x(day) },
            plotLeft = plotLeft,
            plotRight = plotRight,
            gap = gap
        )

        assertEquals(listOf(from, to), placements.map { it.day })
        assertEquals(2, placements.map { it.row }.distinct().size)
        assertDateLabelsSeparated(placements, gap)
        assertTrue(
            placements.all { it.left >= plotLeft && it.right <= plotRight + 0.001f },
            "stacked endpoints must still stay inside the plot: $placements"
        )
    }

    @Test
    fun oneDayWindowPlacesItsSingleDateLabelInsideThePlot() {
        val plotLeft = 40f
        val plotRight = 320f
        val radius = 3f
        val placements = progressDateLabelPlacements(
            from = 5L,
            to = 5L,
            widthFor = { 58f },
            xFor = { day -> progressChartMarkX(day, 5L, 5L, plotLeft, plotRight, radius) },
            plotLeft = plotLeft,
            plotRight = plotRight,
            gap = 6f
        )

        val placement = placements.single()
        assertEquals(5L, placement.day)
        assertEquals(0, placement.row)
        assertEquals(progressChartMarkX(5L, 5L, 5L, plotLeft, plotRight, radius), placement.left)
        assertTrue(placement.left >= plotLeft && placement.right <= plotRight)
    }

    @Test
    fun axisLabelsUseTheDisplayUnitAndLocaleDecimalSeparator() {
        assertEquals(
            "220,5",
            progressAxisTickLabel(
                value = 100.0,
                weightUnit = WeightUnit.LB,
                format = PresentationNumberFormat(','),
                decimals = 1
            )
        )
        // Volume shares the kilogram-reps magnitude, so only the unit factor is applied.
        assertEquals(
            "500",
            progressAxisTickLabel(
                value = 500.0,
                weightUnit = WeightUnit.KG,
                format = PresentationNumberFormat('.'),
                decimals = 0
            )
        )
        val fractionalAxis = ProgressAxis(0.0, 1.0, 0.5, listOf(0.0, 0.5, 1.0))
        assertEquals(1, progressAxisLabelDecimals(fractionalAxis))
    }

    @Test
    fun chartPointsUseTheSelectedMetricAndSkipMissingObservations() {
        val days = listOf(
            day(0L, volume = 500.0, estimate = null),
            day(1L, volume = null, estimate = 120.0),
            day(2L, volume = 900.0, estimate = 130.0)
        )

        assertEquals(
            listOf(500.0, 900.0),
            progressChartPoints(days, ProgressMetric.EXTERNAL_VOLUME).map { it.value }
        )
        assertEquals(
            listOf(120.0, 130.0),
            progressChartPoints(days, ProgressMetric.ESTIMATED_ONE_REP_MAX).map { it.value }
        )
        assertEquals(
            listOf(1L, 2L),
            progressChartPoints(days, ProgressMetric.ESTIMATED_ONE_REP_MAX).map { it.localEpochDay }
        )
        assertTrue(
            progressChartPoints(days.take(1), ProgressMetric.ESTIMATED_ONE_REP_MAX).isEmpty()
        )
    }

    @Test
    fun consecutiveObservationsAreJoinedAndGapsDoNotCreateInventedPoints() {
        val points = listOf(
            point(day = 4L, value = 0.0),
            point(day = 1L, value = 10.0),
            point(day = 9L, value = 30.0)
        )
        val segments = progressChartSegments(points)

        assertEquals(2, segments.size)
        assertEquals(1L, segments[0].from.localEpochDay)
        assertEquals(4L, segments[0].to.localEpochDay)
        assertEquals(4L, segments[1].from.localEpochDay)
        assertEquals(9L, segments[1].to.localEpochDay)
        // The unlogged days stay absent: the only points remain the three observations.
        assertEquals(
            3,
            progressChartPoints(
                points.map { day(it.localEpochDay, it.value, null) },
                ProgressMetric.EXTERNAL_VOLUME
            ).size
        )
        assertTrue(progressChartSegments(points.take(1)).isEmpty())
        assertTrue(progressChartSegments(emptyList()).isEmpty())
    }

    @Test
    fun bothMetricsJoinTheirOwnConsecutiveObservationsWithoutInventingDays() {
        val days = listOf(
            day(localEpochDay = 0L, volume = 500.0, estimate = null),
            day(localEpochDay = 3L, volume = 700.0, estimate = 120.0),
            day(localEpochDay = 10L, volume = null, estimate = 130.0)
        )

        val volumePoints = progressChartPoints(days, ProgressMetric.EXTERNAL_VOLUME)
        val volumeSegments = progressChartSegments(volumePoints)
        assertEquals(1, volumeSegments.size)
        assertEquals(0L, volumeSegments.single().from.localEpochDay)
        assertEquals(3L, volumeSegments.single().to.localEpochDay)
        assertEquals(500.0, volumeSegments.single().from.value)
        assertEquals(700.0, volumeSegments.single().to.value)
        // The volume line spans unlogged days 1–2, but the axis still contains both actual endpoints.
        val volumeAxis = progressAxis(volumePoints, ProgressMetric.EXTERNAL_VOLUME)
        assertTrue(volumePoints.all { it.value in volumeAxis.min..volumeAxis.max })

        val estimateSegments = progressChartSegments(
            progressChartPoints(days, ProgressMetric.ESTIMATED_ONE_REP_MAX)
        )
        assertEquals(1, estimateSegments.size)
        assertEquals(3L, estimateSegments.single().from.localEpochDay)
        assertEquals(10L, estimateSegments.single().to.localEpochDay)
        assertEquals(120.0, estimateSegments.single().from.value)
        assertEquals(130.0, estimateSegments.single().to.value)
        // Day 0 carries no estimate, so it is absent rather than a fabricated starting point.
        assertTrue(
            estimateSegments.none {
                it.from.localEpochDay == 0L || it.to.localEpochDay == 0L
            }
        )

        val allSegments = volumeSegments + estimateSegments
        assertEquals(
            allSegments.size,
            allSegments.map { it.from.localEpochDay to it.to.localEpochDay }.distinct().size
        )
    }

    @Test
    fun estimatesInTheChartComeFromTheDomainCalculatorsEligibility() {
        val calculator = ExerciseProgressCalculator(
            ExerciseProgressRequest(
                exerciseId = "squat",
                dayFrom = 0L,
                dayTo = 6L,
                nowMillis = 4L * DAY
            )
        )
        listOf(
            set(1L, load = 100.0, reps = 5, time = DAY),
            set(2L, load = 100.0, reps = 16, time = 2L * DAY),
            WorkoutSet(
                id = 5L,
                exerciseId = "squat",
                reps = null,
                weightKg = null,
                loadKind = LoadKind.BODYWEIGHT,
                performedAtMillis = 2L * DAY + 1L,
                durationSeconds = 40
            ),
            WorkoutSet(
                id = 6L,
                exerciseId = "squat",
                reps = null,
                weightKg = 20.0,
                loadKind = LoadKind.EXTERNAL,
                performedAtMillis = 2L * DAY + 2L,
                distanceMeters = 400
            ),
            WorkoutSet(
                id = 7L,
                exerciseId = "squat",
                reps = null,
                weightKg = null,
                loadKind = LoadKind.BODYWEIGHT,
                performedAtMillis = 2L * DAY + 3L,
                forceNewtons = 250.0
            ),
            set(3L, load = 50.0, reps = 5, time = 3L * DAY, kind = LoadKind.BODYWEIGHT),
            set(4L, load = 20.0, reps = 5, time = 3L * DAY + 1L, kind = LoadKind.ADDED),
            set(
                8L,
                load = 100.0,
                reps = 5,
                time = 3L * DAY + 2L,
                cluster = WorkoutClusterSummary(ClusterKind.DROP_SET, 2, 1)
            ),
            set(9L, load = 200.0, reps = 5, time = 4L * DAY, warmup = true),
            set(10L, load = 100.0, reps = 5, time = 5L * DAY)
        ).forEach(calculator::accept)
        val progress = calculator.result()

        // Only the external, positive-load, 1–15-rep working set is eligible; warm-up and
        // future-dated rows are excluded from the day trend entirely.
        assertEquals(3, progress.days.size)
        val estimates = progressChartPoints(progress.days, ProgressMetric.ESTIMATED_ONE_REP_MAX)
        assertEquals(listOf(1L), estimates.map { it.localEpochDay })
        assertEquals(100.0 * (1.0 + 5.0 / 30.0), estimates.single().value, 1e-9)

        val volumes = progressChartPoints(progress.days, ProgressMetric.EXTERNAL_VOLUME)
        assertEquals(listOf(500.0, 1600.0, 500.0), volumes.map { it.value })
    }
}

private fun assertDateLabelsSeparated(placements: List<ProgressDateLabelPlacement>, gap: Float) {
    placements.groupBy { it.row }.values.forEach { row ->
        row.sortedBy { it.left }.zipWithNext().forEach { (earlier, later) ->
            assertTrue(
                later.left >= earlier.right + gap,
                "date labels ${earlier.day} and ${later.day} overlap or miss the ${gap}px gap"
            )
        }
    }
}

private fun point(day: Long, value: Double) = ProgressChartPoint(day, value)

private fun day(localEpochDay: Long, volume: Double?, estimate: Double?) = ProgressDay(
    localEpochDay = localEpochDay,
    workingSets = 1,
    totalReps = 5L,
    externalVolumeKg = volume,
    bestLoads = emptyList(),
    bestReps = emptyList(),
    estimatedOneRepMax = estimate?.let {
        ProgressRecord(
            key = ProgressRecordKey(
                type = ProgressRecordType.ESTIMATED_ONE_REP_MAX,
                loadKind = LoadKind.EXTERNAL
            ),
            value = it,
            setIds = emptyList()
        )
    }
)

private fun set(
    id: Long,
    load: Double?,
    reps: Int,
    time: Long,
    kind: LoadKind = LoadKind.EXTERNAL,
    warmup: Boolean = false,
    cluster: WorkoutClusterSummary? = null
) = WorkoutSet(
    id = id,
    exerciseId = "squat",
    reps = reps,
    weightKg = load,
    loadKind = kind,
    performedAtMillis = time,
    isWarmup = warmup,
    cluster = cluster
)

private const val DAY = 86_400_000L
