package com.hydrafit.app.feature.settings

import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.TrainingGoal
import com.hydrafit.app.core.domain.unit.DistanceUnit
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.userdata.settings.AppColorMode
import com.hydrafit.app.core.userdata.settings.AppThemeId
import hydrafit.feature.settings.generated.resources.Res
import hydrafit.feature.settings.generated.resources.settings_appearance_mode_dark
import hydrafit.feature.settings.generated.resources.settings_appearance_mode_light
import hydrafit.feature.settings.generated.resources.settings_appearance_mode_system
import hydrafit.feature.settings.generated.resources.settings_distance_unit_feet
import hydrafit.feature.settings.generated.resources.settings_distance_unit_meters
import hydrafit.feature.settings.generated.resources.settings_engine_deterministic
import hydrafit.feature.settings.generated.resources.settings_engine_gemini
import hydrafit.feature.settings.generated.resources.settings_engine_groq
import hydrafit.feature.settings.generated.resources.settings_engine_local_llm
import hydrafit.feature.settings.generated.resources.settings_goal_balanced
import hydrafit.feature.settings.generated.resources.settings_goal_endurance
import hydrafit.feature.settings.generated.resources.settings_goal_hypertrophy
import hydrafit.feature.settings.generated.resources.settings_goal_strength
import hydrafit.feature.settings.generated.resources.settings_theme_gold_nuva
import hydrafit.feature.settings.generated.resources.settings_theme_makuta
import hydrafit.feature.settings.generated.resources.settings_theme_mata_air
import hydrafit.feature.settings.generated.resources.settings_theme_mata_earth
import hydrafit.feature.settings.generated.resources.settings_theme_mata_fire
import hydrafit.feature.settings.generated.resources.settings_theme_mata_ice
import hydrafit.feature.settings.generated.resources.settings_theme_mata_stone
import hydrafit.feature.settings.generated.resources.settings_theme_mata_water
import hydrafit.feature.settings.generated.resources.settings_theme_quiet_strength
import hydrafit.feature.settings.generated.resources.settings_unit_kg
import hydrafit.feature.settings.generated.resources.settings_unit_lb
import org.jetbrains.compose.resources.StringResource

internal fun PlannerEngineId.labelResource(): StringResource = when (this) {
    PlannerEngineId.DETERMINISTIC -> Res.string.settings_engine_deterministic
    PlannerEngineId.GEMINI_API -> Res.string.settings_engine_gemini
    PlannerEngineId.GROQ_API -> Res.string.settings_engine_groq
    PlannerEngineId.LOCAL_LLM -> Res.string.settings_engine_local_llm
}

internal fun TrainingGoal.labelResource(): StringResource = when (this) {
    TrainingGoal.BALANCED -> Res.string.settings_goal_balanced
    TrainingGoal.STRENGTH -> Res.string.settings_goal_strength
    TrainingGoal.HYPERTROPHY -> Res.string.settings_goal_hypertrophy
    TrainingGoal.ENDURANCE -> Res.string.settings_goal_endurance
}

internal fun AppThemeId.labelResource(): StringResource = when (this) {
    AppThemeId.QUIET_STRENGTH -> Res.string.settings_theme_quiet_strength
    AppThemeId.MATA_FIRE -> Res.string.settings_theme_mata_fire
    AppThemeId.MATA_WATER -> Res.string.settings_theme_mata_water
    AppThemeId.MATA_AIR -> Res.string.settings_theme_mata_air
    AppThemeId.MATA_EARTH -> Res.string.settings_theme_mata_earth
    AppThemeId.MATA_STONE -> Res.string.settings_theme_mata_stone
    AppThemeId.MATA_ICE -> Res.string.settings_theme_mata_ice
    AppThemeId.MAKUTA -> Res.string.settings_theme_makuta
    AppThemeId.GOLD_NUVA -> Res.string.settings_theme_gold_nuva
}

internal fun AppColorMode.labelResource(): StringResource = when (this) {
    AppColorMode.SYSTEM -> Res.string.settings_appearance_mode_system
    AppColorMode.LIGHT -> Res.string.settings_appearance_mode_light
    AppColorMode.DARK -> Res.string.settings_appearance_mode_dark
}

internal fun WeightUnit.labelResource(): StringResource = when (this) {
    WeightUnit.KG -> Res.string.settings_unit_kg
    WeightUnit.LB -> Res.string.settings_unit_lb
}

internal fun DistanceUnit.labelResource(): StringResource = when (this) {
    DistanceUnit.METERS -> Res.string.settings_distance_unit_meters
    DistanceUnit.FEET -> Res.string.settings_distance_unit_feet
}
