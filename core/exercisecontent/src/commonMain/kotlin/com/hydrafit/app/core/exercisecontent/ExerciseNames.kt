package com.hydrafit.app.core.exercisecontent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.hydrafit.app.core.domain.equipment.matchesExerciseNameQuery
import hydrafit.core.exercisecontent.generated.resources.Res
import hydrafit.core.exercisecontent.generated.resources.allStringResources
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Translated names for the seeded exercises, looked up by exercise id when a name is shown.
 *
 * Stored names stay English: the database, backups, exports, imports, accepted plans and the Groq
 * prompt all keep the name they have today. A name is translated only for display, and only while the
 * stored name is still the built-in English name for that id ([isBuiltInName]): a renamed built-in, a
 * custom exercise and any copy that no longer matches are shown exactly as stored. A rename to exactly
 * the English name is indistinguishable from no rename and is translated too.
 */
object ExerciseNames {

    /** The built-in English name of every seeded exercise, by id, as `DefaultExercises` seeds it. */
    val canonicalEnglish: Map<String, String> = mapOf(
        "barbell-bench-press" to "Barbell Bench Press",
        "dumbbell-bench-press" to "Dumbbell Bench Press",
        "incline-dumbbell-press" to "Incline Dumbbell Press",
        "push-up" to "Push-up",
        "incline-push-up" to "Incline Push-up",
        "cable-fly" to "Cable Fly",
        "band-chest-press" to "Band Chest Press",
        "dips" to "Dips",
        "barbell-row" to "Barbell Row",
        "dumbbell-row" to "Dumbbell Row",
        "pull-up" to "Pull-up",
        "chin-up" to "Chin-up",
        "lat-pulldown" to "Lat Pulldown",
        "seated-cable-row" to "Seated Cable Row",
        "band-row" to "Band Row",
        "band-pull-apart" to "Band Pull-Apart",
        "overhead-press" to "Overhead Press",
        "dumbbell-shoulder-press" to "Dumbbell Shoulder Press",
        "lateral-raise" to "Lateral Raise",
        "cable-lateral-raise" to "Cable Lateral Raise",
        "face-pull" to "Face Pull",
        "barbell-curl" to "Barbell Curl",
        "dumbbell-curl" to "Dumbbell Curl",
        "band-curl" to "Band Curl",
        "triceps-pushdown" to "Triceps Pushdown",
        "overhead-triceps-extension" to "Overhead Triceps Extension",
        "close-grip-bench-press" to "Close-Grip Bench Press",
        "back-squat" to "Back Squat",
        "front-squat" to "Front Squat",
        "goblet-squat" to "Goblet Squat",
        "bodyweight-squat" to "Bodyweight Squat",
        "bulgarian-split-squat" to "Bulgarian Split Squat",
        "leg-press" to "Leg Press",
        "romanian-deadlift" to "Romanian Deadlift",
        "dumbbell-rdl" to "Dumbbell Romanian Deadlift",
        "conventional-deadlift" to "Conventional Deadlift",
        "hip-thrust" to "Hip Thrust",
        "glute-bridge" to "Glute Bridge",
        "leg-curl" to "Leg Curl",
        "kettlebell-swing" to "Kettlebell Swing",
        "standing-calf-raise" to "Dumbbell Calf Raise",
        "standing-calf-raise-machine" to "Standing Calf Raise Machine",
        "bodyweight-calf-raise" to "Bodyweight Calf Raise",
        "plank" to "Plank",
        "hanging-leg-raise" to "Hanging Leg Raise",
        "cable-crunch" to "Cable Crunch",
        "hammer-curl" to "Hammer Curl",
        "supinated-curl" to "Supinated Curl",
        "preacher-curl" to "Preacher Curl",
        "close-grip-pulldown" to "Close-Grip Pulldown",
        "wide-grip-pulldown" to "Wide-Grip Pulldown",
        "incline-barbell-press" to "Incline Barbell Press",
        "decline-barbell-press" to "Decline Barbell Press",
        "ez-bar-upright-row" to "EZ Bar Upright Row",
        "seated-ez-bar-curl" to "Seated EZ Bar Curl",
        "trap-bar-deadlift" to "Trap Bar Deadlift",
        "dumbbell-lunge" to "Dumbbell Lunge",
        "barbell-shoulder-press" to "Barbell Shoulder Press",
        "ab-roll" to "Ab Roll",
        "leg-extension" to "Leg Extension",
        "standing-dumbbell-side-bend" to "Standing Dumbbell Side Bend",
        "dumbbell-triceps-overhead-extension" to "Dumbbell Triceps Overhead Extension",
        "dumbbell-front-to-lateral-raise-combo" to "Dumbbell Front to Lateral Raise combo",
        "bench-press-with-chains" to "Bench Press with Chains",
        "cable-chest-press" to "Cable Chest Press",
        "handstand-push-ups" to "Handstand Push-Ups",
        "incline-push-up-medium" to "Incline Push-Up Medium",
        "incline-push-up-wide" to "Incline Push-Up Wide",
        "push-up-to-side-plank" to "Push Up to Side Plank",
        "push-up-wide" to "Push-Up Wide",
        "single-arm-push-up" to "Single-Arm Push-Up",
        "cable-shoulder-press" to "Cable Shoulder Press",
        "dip-machine" to "Dip Machine",
        "double-kettlebell-push-press" to "Double Kettlebell Push Press",
        "push-press" to "Push Press",
        "push-press-behind-the-neck" to "Push Press - Behind the Neck",
        "seated-cable-shoulder-press" to "Seated Cable Shoulder Press",
        "shoulder-press-with-bands" to "Shoulder Press - With Bands",
        "standing-military-press" to "Standing Military Press",
        "barbell-rear-delt-row" to "Barbell Rear Delt Row",
        "bent-over-barbell-row" to "Bent Over Barbell Row",
        "dumbbell-incline-row" to "Dumbbell Incline Row",
        "one-arm-dumbbell-row" to "One-Arm Dumbbell Row",
        "shotgun-row" to "Shotgun Row",
        "t-bar-row-with-handle" to "T-Bar Row with Handle",
        "upright-barbell-row" to "Upright Barbell Row",
        "upright-cable-row" to "Upright Cable Row",
        "close-grip-front-lat-pulldown" to "Close-Grip Front Lat Pulldown",
        "full-range-of-motion-lat-pulldown" to "Full Range-Of-Motion Lat Pulldown",
        "one-arm-lat-pulldown" to "One Arm Lat Pulldown",
        "underhand-cable-pulldowns" to "Underhand Cable Pulldowns",
        "v-bar-pulldown" to "V-Bar Pulldown",
        "v-bar-pullup" to "V-Bar Pullup",
        "wide-grip-pulldown-behind-the-neck" to "Wide-Grip Pulldown Behind The Neck",
        "wide-grip-rear-pull-up" to "Wide-Grip Rear Pull-Up",
        "box-squat" to "Box Squat",
        "dumbbell-squat" to "Dumbbell Squat",
        "hack-squat" to "Hack Squat",
        "kneeling-squat" to "Kneeling Squat",
        "olympic-squat" to "Olympic Squat",
        "overhead-squat" to "Overhead Squat",
        "squat-jerk" to "Squat Jerk",
        "squat-with-bands" to "Squat with Bands",
        "band-good-morning" to "Band Good Morning",
        "barbell-hip-thrust" to "Barbell Hip Thrust",
        "cable-deadlifts" to "Cable Deadlifts",
        "deadlift-with-bands" to "Deadlift with Bands",
        "deadlift-with-chains" to "Deadlift with Chains",
        "deficit-deadlift" to "Deficit Deadlift",
        "good-morning" to "Good Morning",
        "sumo-deadlift" to "Sumo Deadlift",
        "barbell-lunge" to "Barbell Lunge",
        "barbell-step-ups" to "Barbell Step Ups",
        "barbell-walking-lunge" to "Barbell Walking Lunge",
        "dumbbell-rear-lunge" to "Dumbbell Rear Lunge",
        "dumbbell-step-ups" to "Dumbbell Step Ups",
        "elevated-back-lunge" to "Elevated Back Lunge",
        "lunge-pass-through" to "Lunge Pass Through",
        "step-up-with-knee-raise" to "Step-up with Knee Raise",
        "barbell-seated-calf-raise" to "Barbell Seated Calf Raise",
        "calf-press" to "Calf Press",
        "calf-raise-on-a-dumbbell" to "Calf Raise On A Dumbbell",
        "calf-raises-with-bands" to "Calf Raises - With Bands",
        "seated-calf-raise" to "Seated Calf Raise",
        "smith-machine-calf-raise" to "Smith Machine Calf Raise",
        "standing-barbell-calf-raise" to "Standing Barbell Calf Raise",
        "standing-dumbbell-calf-raise" to "Standing Dumbbell Calf Raise",
        "cable-crossover" to "Cable Crossover",
        "cable-iron-cross" to "Cable Iron Cross",
        "decline-dumbbell-flyes" to "Decline Dumbbell Flyes",
        "dumbbell-flyes" to "Dumbbell Flyes",
        "incline-cable-flye" to "Incline Cable Flye",
        "incline-dumbbell-flyes" to "Incline Dumbbell Flyes",
        "incline-dumbbell-flyes-with-a-twist" to "Incline Dumbbell Flyes - With A Twist",
        "low-cable-crossover" to "Low Cable Crossover",
        "cable-preacher-curl" to "Cable Preacher Curl",
        "concentration-curls" to "Concentration Curls",
        "ez-bar-curl" to "EZ-Bar Curl",
        "incline-hammer-curls" to "Incline Hammer Curls",
        "lying-cable-curl" to "Lying Cable Curl",
        "overhead-cable-curl" to "Overhead Cable Curl",
        "reverse-cable-curl" to "Reverse Cable Curl",
        "spider-curl" to "Spider Curl",
        "band-skull-crusher" to "Band Skull Crusher",
        "cable-lying-triceps-extension" to "Cable Lying Triceps Extension",
        "cable-one-arm-tricep-extension" to "Cable One Arm Tricep Extension",
        "ez-bar-skullcrusher" to "EZ-Bar Skullcrusher",
        "low-cable-triceps-extension" to "Low Cable Triceps Extension",
        "lying-triceps-press" to "Lying Triceps Press",
        "seated-triceps-press" to "Seated Triceps Press",
        "tricep-dumbbell-kickback" to "Tricep Dumbbell Kickback",
        "cable-rear-delt-fly" to "Cable Rear Delt Fly",
        "front-cable-raise" to "Front Cable Raise",
        "front-dumbbell-raise" to "Front Dumbbell Raise",
        "front-two-dumbbell-raise" to "Front Two-Dumbbell Raise",
        "lying-rear-delt-raise" to "Lying Rear Delt Raise",
        "one-arm-side-laterals" to "One-Arm Side Laterals",
        "reverse-flyes" to "Reverse Flyes",
        "side-lateral-raise" to "Side Lateral Raise",
        "band-hip-adductions" to "Band Hip Adductions",
        "butt-lift-bridge" to "Butt Lift (Bridge)",
        "cable-hip-adduction" to "Cable Hip Adduction",
        "leg-lift" to "Leg Lift",
        "lying-leg-curls" to "Lying Leg Curls",
        "seated-leg-curl" to "Seated Leg Curl",
        "single-leg-glute-bridge" to "Single Leg Glute Bridge",
        "standing-leg-curl" to "Standing Leg Curl",
        "crunches" to "Crunches",
        "decline-crunch" to "Decline Crunch",
        "reverse-crunch" to "Reverse Crunch",
        "rope-crunch" to "Rope Crunch",
        "scissor-kick" to "Scissor Kick",
        "sit-up" to "Sit-Up",
        "stomach-vacuum" to "Stomach Vacuum",
        "toe-touchers" to "Toe Touchers",
        "flat-bench-cable-fly" to "Flat Bench Cable Fly",
        "single-arm-cable-crossover" to "Single-Arm Cable Crossover",
        "seated-single-arm-cable-row" to "Seated Single-Arm Cable Row",
        "dumbbell-floor-press" to "Dumbbell Floor Press",
        "seated-arnold-dumbbell-press" to "Seated Arnold Dumbbell Press",
        "single-arm-kettlebell-row" to "Single-Arm Kettlebell Row",
        "single-leg-kettlebell-deadlift" to "Single-Leg Kettlebell Deadlift",
        "incline-dumbbell-curl" to "Incline Dumbbell Curl",
        "single-leg-cable-kickback" to "Single-Leg Cable Kickback",
        "bicycle-crunch" to "Bicycle Crunch",
        "machine-pec-deck-fly" to "Machine Pec Deck Fly",
        "reverse-machine-fly" to "Reverse Machine Fly",
        "seated-dumbbell-wrist-curl" to "Seated Dumbbell Wrist Curl",
        "seated-dumbbell-wrist-extension" to "Seated Dumbbell Wrist Extension",
        "dumbbell-shrug" to "Dumbbell Shrug",
        "ez-bar-shrug" to "EZ Bar Shrug",
        "trap-bar-shrug" to "Trap Bar Shrug",
        "barbell-shrug" to "Barbell Shrug",
        "forehead-neck-bridge" to "Forehead Neck Bridge",
        "self-resisted-neck-lateral-flexion" to "Self-Resisted Neck Lateral Flexion",
        "wide-grip-bench-press" to "Wide-Grip Bench Press",
        "bench-dips" to "Bench Dips",
        "feet-elevated-bench-dips" to "Feet-Elevated Bench Dips"
    )

    /** True when [storedName] is still the built-in English name of [exerciseId]. */
    fun isBuiltInName(exerciseId: String, storedName: String): Boolean =
        canonicalEnglish[exerciseId] == storedName

    /** The string resource key holding [exerciseId]'s translated name. */
    fun resourceKey(exerciseId: String): String = "exercise_name_" + exerciseId.replace('-', '_')

    /** The translated-name resource of a built-in exercise, or null for any other id. */
    fun resourceFor(exerciseId: String): StringResource? = if (exerciseId in
        canonicalEnglish
    ) {
        Res.allStringResources[resourceKey(exerciseId)]
    } else {
        null
    }
}

/**
 * The current language's built-in exercise names, by id. Plain data so UI-state code can search and
 * sort with it; screens read it from [LocalExerciseNames], which [ProvideExerciseNames] fills from
 * the resources of the language in use and refreshes when the language changes.
 */
class ExerciseNameTable(private val translatedById: Map<String, String>) {

    /** The name to show: the translation while [storedName] is the built-in name, else as stored. */
    fun display(exerciseId: String, storedName: String): String =
        if (ExerciseNames.isBuiltInName(exerciseId, storedName)) {
            translatedById[exerciseId] ?: storedName
        } else {
            storedName
        }

    /**
     * True when the shown name, or the English name of an untouched built-in, matches [query]: a user
     * who knows the English name finds it in any language, while a renamed exercise matches only its
     * own name.
     */
    fun matches(exerciseId: String, storedName: String, query: String): Boolean =
        matchesExerciseNameQuery(display(exerciseId, storedName), query) ||
            (
                ExerciseNames.isBuiltInName(exerciseId, storedName) &&
                    matchesExerciseNameQuery(storedName, query)
                )

    /** The key lists sort by: the shown name, with accented letters next to their base letters. */
    fun sortKey(exerciseId: String, storedName: String): String =
        foldForSort(display(exerciseId, storedName))

    companion object {
        /** No translations: every name as stored. The default outside a provider (tests, previews). */
        val Stored: ExerciseNameTable = ExerciseNameTable(emptyMap())
    }
}

/** Lowercase with the diacritics of the app's languages removed, for ordering only. */
internal fun foldForSort(name: String): String = buildString(name.length) {
    name.lowercase().forEach { char ->
        val index = ACCENTED.indexOf(char)
        append(if (index >= 0) UNACCENTED[index] else char)
    }
}

private const val ACCENTED = "áàâäãåčćďéèêëěíìîïľĺňńñóòôöõőřŕšśťúùûüůűýÿžźżß"
private const val UNACCENTED = "aaaaaaccdeeeeeiiiillnnnoooooorrsstuuuuuuyyzzzs"

/** The built-in exercise names of the language in use; [ExerciseNameTable.Stored] outside a provider. */
val LocalExerciseNames = staticCompositionLocalOf { ExerciseNameTable.Stored }

/**
 * Provides [LocalExerciseNames] to [content]. The names are read in composition, so a language change
 * recomposes them; they are never cached in a view model, which outlives the activity recreation.
 */
@Composable
fun ProvideExerciseNames(content: @Composable () -> Unit) {
    val translated = ExerciseNames.canonicalEnglish.keys.mapNotNull { id ->
        ExerciseNames.resourceFor(id)?.let { id to stringResource(it) }
    }.toMap()
    // Keyed on the map's value, so recomposing with the same language keeps the same table and the
    // static local does not invalidate the whole app below it.
    val table = remember(translated) { ExerciseNameTable(translated) }
    CompositionLocalProvider(
        LocalExerciseNames provides table,
        content = content
    )
}

/** [exerciseId]'s name as shown in the language in use; see [ExerciseNames]. */
@Composable
fun exerciseDisplayName(exerciseId: String, storedName: String): String =
    LocalExerciseNames.current.display(exerciseId, storedName)
