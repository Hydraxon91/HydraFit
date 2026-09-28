package com.hydrafit.app.core.domain.fatigue

class CalculateMuscleFatigueUseCase(
    private val calculator: FatigueCalculator = FatigueCalculator()
) {
    operator fun invoke(sets: List<LoggedSet>, nowMillis: Long): Map<MuscleGroup, Double> =
        calculator.calculate(sets, nowMillis)
}
