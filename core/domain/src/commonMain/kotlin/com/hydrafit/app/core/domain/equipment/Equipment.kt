package com.hydrafit.app.core.domain.equipment

/** A selectable piece of equipment in the user's inventory. */
data class Equipment(val id: EquipmentTag, val name: String, val isBuiltIn: Boolean)
