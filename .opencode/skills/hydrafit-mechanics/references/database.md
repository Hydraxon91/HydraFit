# SQLDelight recipe and migration fixtures

Paths are relative to the repository root; package shorthand is defined in
`../SKILL.md`. Read current source and relevant tests before using this reference.

Schema directory:
`core/database/src/commonMain/sqldelight/com/hydrafit/app/core/database/`.

Query files: `Equipment.sq`, `Exercise.sq`, `ExerciseExclusion.sq`, `ExerciseOverride.sq`,
`ExercisePreference.sq`, `PersonalRecord.sq`, `PlanHistory.sq`, `PlannerEngine.sq`,
`PlanVolumeExplanation.sq`, `PlanVolumeExplanationState.sq`, `RoutineTemplate.sq`,
`TrainingSchedule.sq`, `UserEquipment.sq`, `WorkoutLog.sq`, and `WorkoutSession.sq`.
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
monotonic set-start/completion instants. Current schema version is 36. Determine the next version from the current directory/generated Schema rather
than copying this snapshot.

`V1ToCurrentMigrationTest` migrates a v1 database (only the `exercise` table) through
`1.sqm..35.sqm` and asserts the retained values and the legacy→involvement conversion.
SQLDelight `verifyMigrations` is enabled against the committed v1 seed `databases/1.db`;
`./gradlew verifySqlDelightMigration` (run in CI) applies `1.sqm..35.sqm` to it and fails
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
