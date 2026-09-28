package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.Exercise

interface ExerciseCatalog {
    suspend fun all(): List<Exercise>
}
