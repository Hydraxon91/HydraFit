package com.hydrafit.app.core.network

import kotlinx.serialization.Serializable

/** A JSON-schema node describing the structured weekly plan a model must return. */
@Serializable
data class PlanSchema(
    val type: String,
    val description: String? = null,
    val properties: Map<String, PlanSchema>? = null,
    val required: List<String>? = null,
    val items: PlanSchema? = null,
    val enum: List<String>? = null,
    val minItems: Int? = null,
    val maxItems: Int? = null
)
