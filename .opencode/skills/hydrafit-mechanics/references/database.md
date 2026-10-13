# SQLDelight recipe and migration fixtures

Paths are relative to the repository root; package shorthand is defined in
`../SKILL.md`. Read current source and relevant tests before using this reference.

Schema directory:
`core/database/src/commonMain/sqldelight/com/hydrafit/app/core/database/`.

0.7.4 calf clarification adds a query-only `Exercise.sq` label repair, called in the seed
transaction before effective search projections are refreshed. Only the exact old built-in
`standing-calf-raise` label becomes **Dumbbell Calf Raise**; its ID/equipment/load and all overrides
and recorded references remain intact. The machine variant is an additive seed with its own ID.
No table/column changes or migration are involved; both calf IDs are protected from startup
custom-name dedupe. See the catalog recipe for content, provenance and regression checks.

Query files: `Equipment.sq`, `Exercise.sq`, `ExerciseExclusion.sq`, `ExerciseNote.sq`,
`ExerciseOverride.sq`, `ExercisePreference.sq`, `PersonalRecord.sq`, `PlanHistory.sq`,
`PlannerEngine.sq`,
`PlanVolumeExplanation.sq`, `PlanVolumeExplanationState.sq`, `PlanTargetExplanation.sq`,
`PlanTargetExplanationState.sq`, `EntryTargetExplanation.sq`, `RoutineTemplate.sq`,
`TrainingSchedule.sq`, `UserEquipment.sq`, `WorkoutLog.sq`, `WorkoutSession.sq`, `SessionNote.sq` and
`WorkoutHistory.sq`.
`PlanHistory.sq`'s `updateEntryExerciseIdAtPosition` swaps one entry's
`exerciseId`/`exerciseName`/`suggestedWeightKg` in place (no schema change) for
`SubstituteExerciseUseCase`; the entry's `sets`/`reps` are untouched. Editing a
routine template upserts kept workouts/slots by id and deletes the removed ones,
so reordering preserves identity.

`WorkoutLog.sq`'s `selectSetById` and `updateSetValues` support in-place recent-set correction.
`SqlDelightSessionResegmenter.resegmentAfterSetCorrection` validates the stored load shape and
updates reps/weight/RIR in the same transaction as time/session corrections. A changed performed
time or changed reps/load/RIR clears timing provenance and elapsed instants atomically; a no-op
correction preserves them. Only a changed time runs the existing full-history resegmentation
algorithm; otherwise the lookup is bounded and session bounds stay untouched. No schema change or
migration is needed; all snapshots, load kind and occurrence links are retained. A missing row fails
instead of recreating a deleted set.

`N.sqm` migrates from version N to N+1. Migration `34.sqm` adds the set-level
`timingProvenance` value (`UNKNOWN` for existing rows); `35.sqm` adds the optional
monotonic set-start/completion instants; `36.sqm` adds shared rest preferences and
the process-restorable countdown row; `37.sqm` adds the two default-off training-guidance
opt-ins, encoded as checked 0/1 integers; `38.sqm` adds `planTargetExplanation` (recorded
per-slot target reasoning, keyed by `(planId, dayIndex, position)`) and
`planTargetExplanationState` (plan-level deload/attribution context); `39.sqm` adds
`entryTargetExplanation` (recorded reasoning for a frozen activation/occurrence entry, keyed by
`entryKind` + `entryId`; entry ids are AUTOINCREMENT so an abandoned row can never be adopted); `40.sqm`
adds `exerciseNote` (empty is an explicit clear; a missing row means never set). Writes validate the
2,000-character limit and exercise identity in the same transaction. `41.sqm` adds `sessionNote`,
keyed by existing session id with the same absent-versus-empty encoding and length limit; Save/Clear
never create sessions. Existing databases start without session notes. Notes stay attached to session
identity through resegmentation, even when a session becomes empty. `42.sqm` adds the case-folded
search projections `exercise.searchName` and `sessionNote.searchNote` (both default empty);
`unicodeCaseFold` in `:core/domain` rewrites them on every name/note write. `exercise.searchName` holds
the folded *effective* display name and every writer that can change a name routes through
`refreshExerciseSearchName` (seed catalog, custom-exercise add/rename, custom-exercise restore,
override update/reset); the
custom-exercise dedupe merge carries the existing override name forward, so it cannot change a
projection. `SearchProjectionBackfill` rebuilds both projections for every row at startup, which heals
databases upgraded from an earlier version as well as any drift. `43.sqm` adds
`workoutSet_sessionId_performedAt` on `workoutSet(sessionId, performedAt)`: the history reads filter
and group by `sessionId` and order one session's sets by `performedAt`, and without it each of those
shapes planned a full table scan plus a temporary B-tree. Current schema version is 44.
Determine the
next version from the current directory/generated Schema rather than copying this snapshot.

`WorkoutHistory.sq` is query-only (no schema of its own): it projects `workoutSession`, `workoutSet`,
`sessionNote` and the current `exercise` catalog into bounded, keyset-paginated history reads behind
`SqlDelightWorkoutHistoryRepository`. A session qualifies when it has recorded sets or a
session-note row; unassigned sets (`sessionId IS NULL`) appear individually; a stored session id with
no session row is reported as a missing-session entry with its identity preserved. Exercise names are
current catalog names, not historical snapshots, and exercise setup notes are never searched. The
recorded-set pages also carry the stored `occurrenceId` so a correction opened from history keeps the
same occurrence attribution as the Logger's recent-set list. Search
matches the case-folded projections `exercise.searchName` and `sessionNote.searchNote`, produced by
`unicodeCaseFold` (Unicode 17.0.0 default full folding, `:core/domain`); the query pattern is folded
the same way, so matching is Unicode-insensitive and accent-sensitive, and stays literal because
`WorkoutHistoryRequest.searchPattern` still escapes `%`, `_` and the escape character. Folding
happens before ordering and pagination, so a match on any page is found.
`exercise.searchName` folds the *effective* display name an override may have renamed, and the set
pages resolve `COALESCE(exerciseOverride.name, exercise.name)`, so history shows and searches the name
the rest of the app shows.
Ordering is `(timestamp DESC, kindRank ASC, sortId DESC)` with set ids zero-padded so the keyset
comparison stays numeric; each query returns `limit + 1` rows so the repository can report whether a
further page exists. The padded set id keeps its sign, and the repository decodes it exactly as
projected, so a stored identity round-trips unchanged through a cursor. `WorkoutHistoryRequest`/`WorkoutHistorySetRequest` validate page size, day-range
order and cursor/filter agreement in `:core:domain`. The projections are a schema addition
(`42.sqm`).

`restPreference` stores the global default under `GLOBAL` and exercise overrides under
`EXERCISE:<exerciseId>`; an absent global row resolves to 120 seconds. Rest countdown
state is separate, transient user data: it stores a monotonic deadline, duration, boot
identity, open session, occurrence and exercise, and is not exported. Backup restore
clears it because restored workout context is not a continuation of the prior process. A successful
custom-exercise deletion removes that exercise's rest override, preference, exclusion, note and
`exerciseOverride` row in the same transaction, and refuses a non-custom id so a built-in's
dependent rows are never cleared. A manual personal record blocks deletion, like logged sets and
plan/routine references; rejected or failed deletion preserves the exercise and every dependent
record. Existing orphaned records are not automatically purged by this path.
Startup custom-exercise dedupe reassigns a merged custom's override onto the seeded id (the canonical
override wins on conflict), so a merge leaves no dangling override either. It also moves a custom's
setup note onto the seeded id, keeping the canonical note when both exist, mirroring the preference
and rest-override rules.

`RestCountdownRepository.observe()` must emit subsequent saves/clears for app-lifetime alert
reconciliation. `SqlDelightWorkoutScheduleRepository` also implements the narrow
`OccurrenceReplacementRepository`: revision, active context, any set freezing the slot (warm-ups,
entry-linked sets keyed by the entry id, and occurrence-level sets for the same exercise), catalog metadata, current
equipment/exclusions and load ceiling
are checked inside the replacement transaction before updating the preserved slot id and occurrence
revision. The same `freezesOccurrenceSlot` predicate backs the use case's pre-check.

`V1ToCurrentMigrationTest` migrates a v1 database (only the `exercise` table) through
the current migration chain and asserts the retained values, legacy→involvement conversion,
rest preference/countdown/guidance and note tables; target-explanation tables are
covered by their own scoped migration tests (`38.sqm`, `39.sqm`), and the exercise/session-note
tables by `ExerciseNoteMigrationTest` (`40.sqm`) and `SessionNoteMigrationTest` (`41.sqm`); the
`42.sqm` search projections are asserted by `V1ToCurrentMigrationTest`.
SQLDelight `verifyMigrations` is enabled against the committed v1 seed `databases/1.db`;
`./gradlew verifySqlDelightMigration` (run in CI) applies `1.sqm..42.sqm` to it and fails
if the result differs from the current `.sq`. Keep `1.db` fixed — it is the immutable v1
schema, not a current-version snapshot. When the migration chain produces a different
column *order* than `.sq` declares, align the `.sq` order to the chain (no column
added/removed/retyped), as done in 0.6.0 for `exercise`, `exerciseOverride` and
`occurrenceEntry`.

`core/database/build.gradle.kts` declares:

```kotlin
sqldelight {
    databases {
        create("HydraFitDatabase") {
            packageName.set("com.hydrafit.app.core.database")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}
```

Named SQL statements become query methods:

```sql
selectAllSets:
SELECT * FROM workoutSet ORDER BY performedAt, id;
```

```kotlin
database.workoutLogQueries.selectAllSets().executeAsList()
```

Generated files land under
`core/database/build/generated/sqldelight/code/HydraFitDatabase/commonMain/`.
`AndroidDatabaseDriverFactory` and `NativeDatabaseDriverFactory` pass
`HydraFitDatabase.Schema` to their driver; the driver handles creation/upgrade.
The default database filename is `hydrafit.db`.

For a repository test, create `JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)`,
call `HydraFitDatabase.Schema.create(driver)`, and construct the repository.
For a migration test, build the old tables with raw SQL, insert representative
old rows, call `Schema.migrate(driver, from, to)`, and assert retained values.
See `WorkoutSetWeekDayMigrationTest.kt` and `InvolvementConversionMigrationTest.kt`
under `core/database/src/androidHostTest/kotlin/com/hydrafit/app/core/database/`.

Fixture trap: migrating to `Schema.version` runs all later migrations, so the
fixture needs every table they touch. Use an explicit end version for a scoped
test. Generated current queries can also expect columns absent from a scoped
old schema; inspect the particular column via raw SQL where appropriate rather
than disguising an old fixture as the latest schema.

`20.sqm` demonstrates a table rebuild for column removal: Android minSdk 24
predates SQLite 3.35's `DROP COLUMN` support.
