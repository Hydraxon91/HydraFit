package com.hydrafit.app.core.domain.equipment

/** A write context, not historical training data. Revision changes on switches and inventory edits. */
data class EquipmentProfileContext(val profileId: Long, val revision: Long)
