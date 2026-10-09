package com.hydrafit.app.core.database

internal data class ExerciseProfileAlias(
    val id: String,
    val exerciseId: String,
    val language: String,
    val label: String,
    val provenance: String
)

/** Editorial lexical mappings only, not evidence of physiological equivalence. Never locale-filter. */
internal object ExerciseProfileAliases {
    private const val PILOT_PROVENANCE =
        "CAT-02 v1 pilot approved 2026-10-09; editorial lexical mapping"

    val all: List<ExerciseProfileAlias> = listOf(
        ExerciseProfileAlias("alias-en-pullup", "pull-up", "en", "Pullup", PILOT_PROVENANCE),
        ExerciseProfileAlias("alias-en-chinup", "chin-up", "en", "Chinup", PILOT_PROVENANCE),
        ExerciseProfileAlias(
            "alias-de-langhantel-bankdruecken",
            "barbell-bench-press",
            "de",
            "Langhantel-Bankdrücken",
            PILOT_PROVENANCE
        ),
        ExerciseProfileAlias(
            "alias-de-kurzhantel-bankdruecken",
            "dumbbell-bench-press",
            "de",
            "Kurzhantel-Bankdrücken",
            PILOT_PROVENANCE
        )
    )

    /** Search-equivalent labels shared by distinct seed/alias identities need explicit acknowledgment. */
    val acknowledgedCollisions: Set<Set<String>> = emptySet()
}
