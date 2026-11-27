package com.hydrafit.app.core.exercisecontent

import androidx.compose.runtime.Composable
import com.hydrafit.app.core.domain.equipment.Equipment
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import hydrafit.core.exercisecontent.generated.resources.Res
import hydrafit.core.exercisecontent.generated.resources.allStringResources
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Translated names for the built-in equipment, looked up by tag id when a name is shown.
 *
 * Stored names stay English ([EquipmentTag.BUILT_IN_NAMES]): the database, backups, exports and the
 * Groq prompt keep them. Built-in equipment cannot be renamed, so a built-in is always shown in the
 * language in use; custom equipment is shown exactly as the user named it.
 */
object EquipmentNames {

    /** The string resource key holding [tagId]'s translated name. */
    fun resourceKey(tagId: String): String = "equipment_name_" + tagId.lowercase()

    /** The translated-name resource of a built-in tag, or null for custom equipment. */
    fun resourceFor(tagId: String): StringResource? = if (tagId in EquipmentTag.BUILT_IN_NAMES) {
        Res.allStringResources[resourceKey(tagId)]
    } else {
        null
    }
}

/** [tag]'s name in the language in use; a custom tag falls back to [fallback]. */
@Composable
fun equipmentDisplayName(tag: EquipmentTag, fallback: String = tag.displayName): String =
    EquipmentNames.resourceFor(tag.id)?.let { stringResource(it) } ?: fallback

/** [equipment]'s name in the language in use: translated when built in, else as the user named it. */
@Composable
fun equipmentDisplayName(equipment: Equipment): String = if (equipment.isBuiltIn) {
    equipmentDisplayName(equipment.id, fallback = equipment.name)
} else {
    equipment.name
}
