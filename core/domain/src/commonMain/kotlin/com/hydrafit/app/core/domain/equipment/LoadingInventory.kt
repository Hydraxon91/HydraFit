package com.hydrafit.app.core.domain.equipment

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Calculator amounts are integer thousandths of [LoadingInventory.unit], never planner kilograms. */
const val LOADING_SCALE: Int = 1_000
const val MAX_LOADING_TICKS: Int = 2_000 * LOADING_SCALE

@Serializable
data class OwnedPlate(val weightTicks: Int, val count: Int)

@Serializable
data class LoadingBar(
    val id: String,
    val name: String,
    val weightTicks: Int,
    val collarTicks: Int = 0
)

/** Exactly one representation is used: a fixed list, or all three adjustable-range fields. */
@Serializable
data class LoadingDumbbell(
    val id: String,
    val name: String,
    val fixedTicks: List<Int> = emptyList(),
    val minimumTicks: Int? = null,
    val maximumTicks: Int? = null,
    val incrementTicks: Int? = null
)

@Serializable
data class LoadingInventory(
    val unit: String = "KG",
    val plates: List<OwnedPlate> = emptyList(),
    val bars: List<LoadingBar> = emptyList(),
    val dumbbells: List<LoadingDumbbell> = emptyList()
)

fun requireLoadingInventory(inventory: LoadingInventory) {
    require(inventory.unit == "KG" || inventory.unit == "LB")
    require(inventory.plates.size <= 16)
    require(inventory.plates.map { it.weightTicks }.distinct().size == inventory.plates.size)
    inventory.plates.forEach {
        require(it.weightTicks in 1..MAX_LOADING_TICKS)
        require(it.count in 0..100)
    }
    val plateTotal = inventory.plates.sumOf { it.weightTicks.toLong() * it.count }
    require(plateTotal <= MAX_LOADING_TICKS)
    require(inventory.bars.map { it.id }.distinct().size == inventory.bars.size)
    inventory.bars.forEach {
        requireLoadingName(it.id)
        requireLoadingName(it.name)
        require(it.weightTicks in 0..MAX_LOADING_TICKS)
        require(it.collarTicks in 0..MAX_LOADING_TICKS)
        require(plateTotal + it.weightTicks + it.collarTicks <= MAX_LOADING_TICKS)
    }
    require(inventory.dumbbells.map { it.id }.distinct().size == inventory.dumbbells.size)
    inventory.dumbbells.forEach {
        requireLoadingName(it.id)
        requireLoadingName(it.name)
        if (it.fixedTicks.isNotEmpty()) {
            require(it.minimumTicks == null && it.maximumTicks == null && it.incrementTicks == null)
            require(it.fixedTicks.distinct().size == it.fixedTicks.size)
            require(it.fixedTicks.all { weight -> weight in 1..MAX_LOADING_TICKS })
        } else {
            val minimum = requireNotNull(it.minimumTicks)
            val maximum = requireNotNull(it.maximumTicks)
            val increment = requireNotNull(it.incrementTicks)
            require(minimum in 1..MAX_LOADING_TICKS)
            require(maximum in minimum..MAX_LOADING_TICKS)
            require(increment in 1..MAX_LOADING_TICKS)
            require((maximum - minimum) % increment == 0)
        }
    }
}

private fun requireLoadingName(value: String) {
    require(value.isNotBlank() && value.length <= 80 && value == value.trim())
}

/** Shared by persistence and backup validation so stored calculator encodings have one contract. */
object LoadingInventoryJson {
    private val json = Json { encodeDefaults = true }

    fun encode(inventory: LoadingInventory): String {
        requireLoadingInventory(inventory)
        return json.encodeToString(LoadingInventory.serializer(), inventory)
    }

    fun decode(text: String): LoadingInventory =
        json.decodeFromString(LoadingInventory.serializer(), text).also(::requireLoadingInventory)
}
