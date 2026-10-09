# Catalog mechanics

Paths are relative to the repository root; package shorthand is defined in
`../SKILL.md`. Read current source and relevant tests before using this reference.

## Muscle mapping and catalog persistence

The database encodes involvements as comma-separated `MUSCLE:weight` pairs,
for example `CHEST_UPPER:0.7,CHEST_LOWER:0.7,TRICEPS:0.5`.
`ExerciseEncoding.kt` owns encoding and decoding; encoding sorts by muscle name.
`MuscleGroup` has 21 groups (`ADDUCTORS`, `HIP_ABDUCTORS`, `TRAPS`, `NECK` added
2026-10-06; `TRAPS` is excluded from planner volume-deficit targeting and must not
double-count with `UPPER_BACK`); `decodeInvolvements` expands legacy broad names
(`CHEST`/`BACK`/`SHOULDERS`/`CORE`) on read, so historical rows keep contributing.

There are three distinct locations for this map:

1. `exercise.involvements`: catalog defaults or custom exercise data.
2. `exerciseOverride.involvements`: an optional built-in exercise edit.
3. `workoutSet.involvements`: effective muscle mapping snapshotted when logged.

`CustomExerciseDedupe` runs at startup after seeding: a custom exercise whose name
matches a seeded exercise after normalization (trim, collapse whitespace, lowercase) is
merged into the seeded id. History and plan entries are reassigned; the custom's
differing equipment, pattern, unilateral flag, load capability and involvement weights are
preserved as a canonical override (values matching the built-in are not materialized, and a
legacy `UNSPECIFIED` custom never overwrites the curated default); personal records are
merged by weight, then reps, then timestamp; the custom override and row are removed for
legacy catalog identities. CAT-P7 seed IDs are excluded from startup name dedupe so a
pre-existing same-name custom and its references remain intact.
The 21-group set and the muscle-split mechanism are recorded in PLANS.md and
`docs/exercise-catalog-sources.md`.

CAT-02 v1 separates matching from Save identity. Domain `equipment/CatalogExerciseProfile.kt`
defines `CatalogExerciseProfile` and its five-group `ExerciseProfile`; `CatalogProfileMatcher.kt`
returns Unknown/Unique/Ambiguous using `ExerciseNameSearch.kt` normalization plus case-insensitive
exact comparison (blank/separators are unknown, no partial/fuzzy/accent/keyword inference).
`ExerciseCatalog.profileCandidates()` defaults to no candidates for unsupported implementations.
`SqlDelightExerciseCatalog` reads only present seeded/non-custom identities, retaining canonical
names from `DefaultExercises` and applying effective overrides without rounding weights or
converting `UNSPECIFIED`. Catalog-owned `ExerciseProfileAliases.kt` contains only the approved
Pullup/Chinup and Langhantel-Bankdrücken/Kurzhantel-Bankdrücken pilot, in all locales, with stable IDs,
language and editorial provenance. Labels deduplicate by catalog ID; cross-identity collisions
stay ambiguous. Data-quality tests validate targets/duplicates/acknowledged collisions.

Custom add/update reject newly introduced names that collide with a seeded identity, using the same
existing `normalizeExerciseName`. An existing custom matching a CAT-P7 seed can be edited without
changing its normalized name; other-row duplicate checks remain. Aliases/translations/dash equivalence
do not broaden identity. `CustomExerciseException.reason` is `NAME_CONFLICT` for name collisions.
Legacy startup dedupe is unchanged for non-P7 identities; P7-matching existing customs keep their
IDs and references.
See `docs/architecture.md` §1.13 and `docs/exercise-catalog-sources.md` for the approved contract.

Equipment new-custom creation exposes explicit Find profile, ambiguity chooser and
`ExerciseProfileSuggestionDialog` (Suggested catalog profile, not physiological equivalence).
`ExerciseEditorState.touchedGroups` tracks five `ExerciseProfileGroup` groups. Manual callbacks
mark touches even for defaults/restored values; preview checks initially select untouched groups.
`previewProfile` does not edit values/touches; `applySelectedProfile` copies selected groups only,
replaces the involvement map as a whole and protects applied groups. No-selection Apply is a no-op
and disabled in UI. Checkbox edits/preview Cancel do not mutate values/touches. Name edits clear
`suggestion` without clearing the profile/protection; dismissal resets creation state. All four
capabilities, including legacy/unspecified, survive preview/Apply without conversion. Names remain
the user's; Apply never saves, and ordinary Save still trims/validates. Typed name conflicts raise a
localized "Name already in use" popup; Keep editing clears only the conflict and preserves the name,
profile and protection flags. The ViewModel retains six dependencies, uses a revision
to invalidate delayed reads, and does not add/change Koin bindings. No history, IDs, preferences,
exclusions, PRs, prescriptions or working-load suggestions are copied.

`SqlDelightExerciseCatalog` overlays nullable override fields onto catalog rows.
Display groups are derived from the resolved map: weight `>= 0.7` is primary,
and lower positive weights are secondary. The SQL legacy primary/secondary
columns were removed; these remain domain/display concepts.

Domain `equipment/Exercise.kt` provides `effectiveInvolvements`. Its fallback
for an empty explicit map is primary muscles at `1.0` plus secondary muscles at
`0.5`. Fatigue uses `MuscleTarget(muscle, weight)` values.

Editor tiers: None, Low `0.3`, Mid `0.5`, High `0.7`, Primary `1.0`.
Stored values are doubles, not a tier enum.

`DefaultExercises.kt` is the seed catalog (baseline + `DefaultExercisesCatalogC1` +
`DefaultExercisesCatalogP7`). CAT-P7 adds ten sourced identities, all using existing equipment tags,
patterns and muscles; row provenance is in `docs/exercise-catalog-sources.md`. P7 load capability is
explicitly curated, not inferred from equipment; bicycle crunch is `BODYWEIGHT_ONLY`.
`SeedExerciseCatalog` inserts missing rows, backfills involvements only where null,
then idempotently normalizes legacy built-in weights onto the editor tier scale
(`0.6 → 0.7`, `0.4 → 0.5`, `0.2 → 0.3`). It then rewrites built-in rows that still
hold pre-MUS-P1 broad names (`CHEST`/`BACK`/`SHOULDERS`/`CORE`) from the current
seed (`repairLegacyInvolvementNames`; anchored LIKE patterns avoid matching the split
names), so an upgraded install matches a fresh one. Custom rows, user overrides and
historical set snapshots are left untouched. It runs off-main at startup via
`DatabaseStartupMaintenance`. `DefaultExercisesDataQualityTest` asserts every
built-in row satisfies `MovementPatternGuardrail` except five pinned fresh-install
classification conflicts (tracked in `docs/live-testing-2026-10-08.md`).
