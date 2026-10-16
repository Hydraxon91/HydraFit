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
`TrainingSchedule.sq`, `UserEquipment.sq`, `WorkoutLog.sq`, `WorkoutSession.sq`, `SessionNote.sq`,
`WorkoutImport.sq` and
`WorkoutHistory.sq`.

`SqlDelightTrainingCoverageRepository` (0.8.2) observes the existing `selectAllSets`
query off-main. It reads recorded involvements only (null remains unknown), with no
catalog joins or fallback, and retains correction/delete/restore invalidations even
when row counts are unchanged. History is needed for last-trained; no persistent
cache, schema change or new format is introduced.

`ExerciseProgress.sq` is query-only: exercise-keyed history pages include current effective names
through left joins, and replay pages preserve timestamp/id order across 256-row batches. Each page is
bound without an optional-cursor `OR` predicate: a first-page query plus two disjoint continuation
reads (equal timestamp/id range, then strictly earlier/later time) that `SqlDelightExerciseProgressRepository`
concatenates. That keeps every page a direct index seek instead of rescanning the prefix earlier pages
already read. `SqlDelightExerciseProgressRepository` replays one exercise through the request instant
in one off-main transaction, retaining derived outputs rather than all raw rows. Chart day bounds do not
limit all-time record comparisons. The set-count query is an invalidation anchor, not a revision:
do not apply `distinctUntilChanged` to its count, since correction/restore can keep counts unchanged.
The repository is bound by `databaseModule` and consumed read-only by `:feature:progress` and the
summary records section; it adds no stored cache. Migration `46.sqm` adds
`workoutSet_exerciseId_performedAt_id` on `(exerciseId, performedAt, id)` for exercise-scoped
paging and `47.sqm` adds `workoutSet_occurrenceId_exerciseId` on `(occurrenceId, exerciseId)` so the
summary's live occurrence read seeks one occurrence instead of scanning the set index;
`ExerciseProgressIndexMigrationTest` and `WorkoutSetOccurrenceIndexMigrationTest` cover preservation
and indexed query plans for fresh/migrated schemas. The request fixes
`nowMillis` and the UTC offset; future passage/offset changes require a new request from the caller.
Query-only equipment identity repair (0.7.6 C6) adds custom-id movement and per-row equipment-reference
updates in `Equipment.sq`, `Exercise.sq`, `ExerciseOverride.sq` and `TrainingSchedule.sq`.
`SeedEquipmentCatalog` remaps legacy custom equipment on reserved ids transactionally before canonical
seeding, preserving all user/frozen references without rewriting seed exercise definitions. Restore
seeds missing canonical equipment inside its replacement transaction before applying ceilings.
No table/column change or migration is involved; catalog/backup references own the compatibility policy.
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

`N.sqm` migrates from version N to N+1. `45.sqm` adds the two default-off rest-presentation
opt-ins to `trainingGuidancePreference` (added without a CHECK constraint, like the earlier
`workoutSet` columns), creates `workoutReminderPreference` (singleton row: reminders opt-in, a
nullable minutes-past-local-midnight reminder time, and quiet hours) and creates the device-local
`reminderDelivery` table (one posted reminder per `activationId` + local `reminderEpochDay`), which
is deliberately excluded from backups and cleared on restore. `SqlDelightReminderDeliveryRepository`
reads a day with `selectDeliveredAt`, claims it with a `select` + `markDelivered` inside one
transaction (so two delivery paths cannot both win), releases a failed post with `deleteDelivery`,
and prunes days older than a week with `deleteBefore` on each claim; only today's row is ever read.
Because that claim alone cannot stop a post derived from stale schedule or preference reads,
`SqlDelightWorkoutScheduleRepository`'s mutators, the reminder-preference setters and the whole
`restore` replacement each run inside the shared `WorkoutReminderDeliveryGate` and invalidate its
revision token, which the posting path re-checks before its side effects. Migration `34.sqm` adds the set-level
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
shapes planned a full table scan plus a temporary B-tree. `44.sqm` adds the singleton `setupState`
and nullable `preferredWeekdays` rows. Fresh creation starts PENDING/WELCOME; migration starts
RETURNING/WELCOME regardless of workout count. Current schema version is 50.
Migration `49.sqm` adds profile identity/configuration, per-profile equipment entries and active-profile
state. It copies global selection and every ceiling into one active profile; fresh creation starts
empty. `SqlDelightEquipmentProfileRepository` returns coherent snapshots off-main and validates a
global context revision inside each mutation transaction. All mutators bump that revision, including
profile switching, so an editor cannot survive an away-and-back switch. Calculator configuration is
validated by domain `LoadingInventoryJson` before persistence. Equipment adapters read the active
profile; planner sources and Logger warm-up guidance consume one coherent snapshot. Setup validates
its captured profile/revision in the boundary transaction. Profile-scoped queries serve writes;
global custom-equipment deletion uses a SQL existence guard and rejects other-profile references.
Occurrence replacement reads the same active-profile snapshot for availability and ceilings. Live
edits no longer dual-write the legacy `selected_equipment` table or the global `equipment.maxWeightKg`
column; only startup identity repair (`SeedEquipmentCatalog`) and backup restore still touch those
legacy stores, and neither is a behavioral authority, so the active profile is the single authority.
`BodyWeight.sq` (0.8.3) owns `bodyWeightMeasurement` (one row per local civil day, the primary
key making a duplicate date impossible) and single-row `bodyWeightGoal`; `48.sqm` creates both,
so `SqlDelightBodyWeightRepository` keeps "no goal" distinct from any stored value by deleting
the goal row on clear.
Determine the
next version from the current directory/generated Schema rather than copying this snapshot.

`WorkoutImport.sq` (0.9.1) owns `workoutImportProvenance`, the legacy local import ledger: one row per
imported source workout with its provider, candidate key, canonical content and created session id.
It carries no foreign key to `workoutSession` on purpose, so a row survives local deletion of the
imported history and a repeated export cannot silently restore it. `50.sqm` creates the table and its
source index.

`WorkoutImportBucket.sq` (0.9.1 bucket/partition fix) owns `workoutImportBucket` and
`workoutImportBucketSession`. A bucket is one provider/date/title source group with its complete
canonical content and the canonical **partition** of its rows (which row positions form which session,
plus the excluded positions); the child table links each partition ordinal to the local session it
created, and, like provenance, keeps those links after the session is deleted. This is what lets one
source bucket be split into two sessions that may even have identical content, which per-session
provenance cannot represent (its `(source, candidateKey, content)` uniqueness would collapse them).
`SqlDelightWorkoutImportRepository` re-reads the stored bucket inside the commit transaction and
refuses the whole commit when the approved partition or bucket content no longer matches, and still
falls back to the legacy provenance rows for a bucket imported before this table existed.
`SqlDelightBackupRepository` exports and whole-replaces both ledgers with the rest of the user-owned
data. `51.sqm` creates the two tables; current schema version is 52.

`SqlDelightPrescriptionImportRepository` (0.9.2-S2) appends an approved prescription import in one
transaction with **no schema change**: each routine is inserted as new `routineTemplate`/`routineWorkout`/
`routineEntry` rows with fresh identities, and a custom definition is created only when its id is
unused (an id claimed since the preview refuses the whole commit instead of overwriting a local row).
It refuses before the transaction opens when the approved preview carries no routine or still carries
any problem (`NOTHING_TO_IMPORT`, `PREVIEW_NOT_IMPORTABLE`), so a caller that ignores `canImport`
cannot import a subset. It writes nothing outside those tables and the new `exercise` rows, so an
import cannot create an
accepted plan, an activation or a schedule change. The pure resolution it consumes lives in
`:core:domain` `transfer/PreviewPrescriptionImport` (see `backup.md`).

`SqlDelightSetupWriteRepository` implements domain `SetupWriteRepository`. Its transaction compares
the full expected setup state/revision, validates domain inputs and effective catalog identities,
writes the step payload, then advances the boundary. Equipment lookups and PR eligibility checks
are per-id (no catalog scan on the write path). A failed boundary write rolls back every step payload.
Weekday writes share `savePreferredWeekdays` with the standalone preference repository. PR writes
upsert only submitted external records; completion/dismissal never create training data.

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
`./gradlew verifySqlDelightMigration` (run in CI) applies the entire migration chain to it and fails
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
