package com.hydrafit.app.core.domain.equipment

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CatalogProfileMatcherTest {
    @Test
    fun matchesCaseWhitespaceAndAsciiOrUnicodeDashesExactly() {
        val candidate = candidate("close-grip", "Close-grip Bench Press")
        listOf(
            "  CLOSE   GRIP\tBENCH PRESS  ",
            "close-grip bench-press",
            "close\u2010grip bench press",
            "close\u2011grip bench press",
            "close\u2012grip bench press",
            "close\u2013grip bench press",
            "close\u2014grip bench press",
            "close\u2015grip bench press"
        ).forEach { name ->
            assertEquals(CatalogProfileMatch.Unique(candidate), match(name, candidate))
        }
    }

    @Test
    fun blankSeparatorsPartialNamesAndInferenceHaveNoConfidentMatch() {
        val candidate = candidate("press", "Incline One-arm Dumbbell Press")
        listOf("", "  ", " - \u2014 ", "press", "chest", "Incline Dumbbell Press").forEach {
            assertEquals(CatalogProfileMatch.Unknown, match(it, candidate))
        }
    }

    @Test
    fun preservesEquipmentGripAngleAndUnilateralQualifiers() {
        val names = listOf(
            "Barbell Bench Press",
            "Dumbbell Bench Press",
            "Close-grip Barbell Bench Press",
            "Incline Barbell Bench Press",
            "One-arm Dumbbell Bench Press"
        )
        val candidates = names.mapIndexed { index, name -> candidate("id-$index", name) }
        candidates.forEach {
            assertEquals(
                CatalogProfileMatch.Unique(it),
                CatalogProfileMatcher.match(it.canonicalName, candidates)
            )
        }
        assertEquals(
            CatalogProfileMatch.Unknown,
            CatalogProfileMatcher.match("Bench Press", candidates)
        )
    }

    @Test
    fun keepsCanonicalEffectiveAndAllLanguageAliasesAfterRename() {
        val candidate = candidate("bench", "Barbell Bench Press").copy(
            displayName = "My Bench",
            aliases = listOf("Langhantel-Bankdrücken", "Benchpress")
        )
        candidate.matchingLabels.forEach {
            assertEquals(CatalogProfileMatch.Unique(candidate), match(it, candidate))
        }
        assertEquals(CatalogProfileMatch.Unknown, match("Langhantel-Bankdrucken", candidate))
        assertEquals(CatalogProfileMatch.Unknown, match("Bankdrücken", candidate))
    }

    @Test
    fun deduplicatesLabelsAndIdentitiesButRetainsGenuineAmbiguity() {
        val first = candidate("a", "Pull-up").copy(aliases = listOf("Pull up", "Pullup", "Pullup"))
        val second = candidate("b", "Different variation").copy(displayName = "Pullup")
        assertEquals(CatalogProfileMatch.Unique(first), match("Pull up", first))
        assertEquals(
            CatalogProfileMatch.Ambiguous(listOf(first, second)),
            CatalogProfileMatcher.match("Pullup", listOf(second, first, first))
        )
    }

    @Test
    fun returnsExactEffectiveProfileForEveryCapabilityWithoutConversion() {
        ExerciseLoadCapability.entries.forEach { capability ->
            val candidate = candidate("bench", "Bench").copy(
                profile = ExerciseProfile(
                    equipment = setOf(EquipmentTag.DUMBBELL),
                    movementPattern = MovementPattern.HORIZONTAL_PUSH,
                    involvements = mapOf(
                        MuscleGroup.CHEST_UPPER to 0.83,
                        MuscleGroup.TRICEPS to 0.17
                    ),
                    loadCapability = capability,
                    isUnilateral = true
                )
            )
            val result = assertIs<CatalogProfileMatch.Unique>(match("Bench", candidate))
            assertEquals(candidate.profile, result.candidate.profile)
        }
    }

    @Test
    fun unsupportedCatalogDoesNotInventCanonicalNames() = runTest {
        val catalog = object : ExerciseCatalog {
            override suspend fun all(): List<Exercise> = listOf(
                Exercise("renamed", "Effective name only", emptySet(), emptySet())
            )
        }
        assertTrue(catalog.profileCandidates().isEmpty())
    }

    private fun match(name: String, candidate: CatalogExerciseProfile): CatalogProfileMatch =
        CatalogProfileMatcher.match(name, listOf(candidate))

    private fun candidate(id: String, name: String): CatalogExerciseProfile =
        CatalogExerciseProfile(
            catalogId = id,
            canonicalName = name,
            displayName = name,
            profile = ExerciseProfile(
                equipment = emptySet(),
                movementPattern = MovementPattern.CORE,
                involvements = emptyMap(),
                loadCapability = ExerciseLoadCapability.UNSPECIFIED,
                isUnilateral = false
            )
        )
}
